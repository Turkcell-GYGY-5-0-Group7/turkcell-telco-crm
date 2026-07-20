package com.telco.order.application.handler;

import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCreatedEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderType;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.AddonClientResponse;
import com.telco.order.infrastructure.client.CampaignServiceClient;
import com.telco.order.infrastructure.client.CampaignValidationResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a new order: validates customer and tariffs via downstream services, prices each item
 * (applying a campaign discount when eligible, Feature 21.3.3), persists the order aggregate,
 * initialises the saga state, and emits {@code order.created.v1} via the outbox.
 *
 * <p>Idempotency: if the same {@code idempotencyKey} is already present the existing order is
 * returned without any side effects (FR-10).
 *
 * <p>The mediator {@code TransactionBehavior} wraps this handler in a transaction so the JPA
 * insert, saga-state insert, and outbox row commit atomically (ADR-005, ADR-009).
 */
@Component
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderResponse> {

    private static final String OUTBOX_AGGREGATE_TYPE = "order";
    private static final String EVENT_TYPE = "order.created.v1";
    private static final String DISCOUNT_TYPE_PERCENTAGE = "PERCENTAGE";
    private static final String DISCOUNT_TYPE_FIXED_AMOUNT = "FIXED_AMOUNT";

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final CustomerServiceClient customerServiceClient;
    private final ProductCatalogServiceClient productCatalogServiceClient;
    private final CampaignServiceClient campaignServiceClient;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CreateOrderCommandHandler(OrderRepository orderRepository,
                                     SagaStateRepository sagaStateRepository,
                                     CustomerServiceClient customerServiceClient,
                                     ProductCatalogServiceClient productCatalogServiceClient,
                                     CampaignServiceClient campaignServiceClient,
                                     OutboxService outboxService,
                                     AuditLogWriter auditLogWriter) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.customerServiceClient = customerServiceClient;
        this.productCatalogServiceClient = productCatalogServiceClient;
        this.campaignServiceClient = campaignServiceClient;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    /**
     * A priced line item: a tariff snapshot plus the campaign decision applied to it, OR an addon
     * snapshot for ADDON orders (FR-09) - exactly one of {@code tariff}/{@code addon} is non-null.
     */
    private record PricedItem(TariffClientResponse tariff, BigDecimal unitPrice,
                               UUID campaignId, String campaignCode, AddonClientResponse addon) {

        PricedItem(TariffClientResponse tariff, BigDecimal unitPrice, UUID campaignId, String campaignCode) {
            this(tariff, unitPrice, campaignId, campaignCode, null);
        }
    }

    @Override
    public OrderResponse handle(CreateOrderCommand command) {
        // Idempotency check: return existing order if idempotency key already exists.
        Optional<Order> existing = orderRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        // Validate customer exists (throws ResourceNotFoundException or DependencyFailureException).
        customerServiceClient.getCustomer(command.customerId());

        OrderType orderType = command.orderType() == null ? OrderType.NEW_LINE : command.orderType();
        if (orderType != OrderType.NEW_LINE && command.items().size() != 1) {
            // Keeps the provisioning consumer unambiguous: one PLAN_CHANGE/ADDON order = one product.
            throw new BusinessRuleException(orderType + " orders must contain exactly one item");
        }

        // Price each item by product kind (FR-09). Tariff items go through campaign pricing only on
        // NEW_LINE orders (campaigns discount new-line tariff purchases); addon items are priced
        // straight from the catalog addon.
        List<PricedItem> pricedItems = new ArrayList<>();
        for (OrderItemRequest item : command.items()) {
            if (orderType == OrderType.ADDON) {
                if (item.addonCode() == null || item.addonCode().isBlank()) {
                    throw new BusinessRuleException("ADDON order items must carry an addonCode");
                }
                AddonClientResponse addon = productCatalogServiceClient.getAddon(item.addonCode());
                pricedItems.add(new PricedItem(null, addon.price(), null, null, addon));
                continue;
            }
            if (item.tariffId() == null) {
                throw new BusinessRuleException(orderType + " order items must carry a tariffId");
            }
            TariffClientResponse tariff = productCatalogServiceClient.getTariff(item.tariffId());
            if (orderType == OrderType.NEW_LINE) {
                pricedItems.add(priceItem(command.customerId(), item, tariff));
            } else {
                pricedItems.add(new PricedItem(tariff, tariff.monthlyFee(), null, null));
            }
        }

        // Calculate total amount from the (possibly discounted) unit prices.
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < command.items().size(); i++) {
            BigDecimal lineTotal = pricedItems.get(i).unitPrice()
                    .multiply(BigDecimal.valueOf(command.items().get(i).quantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // Create and persist the order aggregate. PLAN_CHANGE/ADDON orders are created CONFIRMED:
        // they carry no upfront payment (payment-service skips them; the fee bills on the next
        // invoice, FR-22), so subscription provisioning fulfils them directly.
        Order order = Order.create(command.customerId(), command.idempotencyKey(), totalAmount,
                command.userId(), orderType, command.subscriptionId());
        for (int i = 0; i < command.items().size(); i++) {
            OrderItemRequest itemReq = command.items().get(i);
            PricedItem priced = pricedItems.get(i);
            if (priced.addon() != null) {
                order.addAddonItem(priced.addon().code(), priced.addon().name(),
                        priced.unitPrice(), itemReq.quantity());
            } else {
                order.addItem(
                        itemReq.tariffId(),
                        priced.tariff().code(),
                        priced.tariff().version(),
                        priced.tariff().name(),
                        priced.unitPrice(),
                        itemReq.quantity(),
                        priced.campaignId(),
                        priced.campaignCode()
                );
            }
        }
        orderRepository.save(order);

        // Initialise saga state for this order.
        SagaState sagaState = SagaState.init(
                order.getId(), "ORDER_CREATED", "PENDING", null);
        sagaStateRepository.save(sagaState);

        // Build event item payloads from the order items. Addon items carry no tariffId; their
        // addonType comes from the pricing snapshot (same index order as order.getItems()).
        List<OrderCreatedEvent.OrderItemPayload> eventItems = new ArrayList<>();
        for (int i = 0; i < order.getItems().size(); i++) {
            var item = order.getItems().get(i);
            AddonClientResponse addon = pricedItems.get(i).addon();
            TariffClientResponse tariff = pricedItems.get(i).tariff();
            eventItems.add(new OrderCreatedEvent.OrderItemPayload(
                    item.getTariffId() == null ? null : item.getTariffId().toString(),
                    item.getTariffName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getCampaignId() == null ? null : item.getCampaignId().toString(),
                    item.getAddonCode(),
                    addon == null ? null : addon.type(),
                    item.getTariffCode(),
                    addon != null ? addon.currency() : (tariff == null ? null : tariff.currency())
            ));
        }

        // Publish order.created.v1 through the transactional outbox (ADR-009).
        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                order.getId().toString(),
                EVENT_TYPE,
                new OrderCreatedEvent(
                        order.getId().toString(),
                        order.getCustomerId().toString(),
                        eventItems,
                        order.getTotalAmount(),
                        order.getIdempotencyKey(),
                        Instant.now().toString(),
                        order.getOrderType().name(),
                        order.getSubscriptionId() == null ? null : order.getSubscriptionId().toString()
                )
        );

        auditLogWriter.log("ORDER_CREATED", "Order", order.getId().toString(),
                Map.of("customerId", command.customerId().toString(),
                        "totalAmount", order.getTotalAmount().toPlainString()));

        return OrderResponse.from(order);
    }

    /**
     * Asks campaign-service (via the fail-open {@link CampaignServiceClient}) whether a discount
     * applies to this line item and computes the resulting {@code unitPrice}. Never throws:
     * {@code CampaignServiceClient.validate(...)} itself never propagates a failure (ADR-027 Decision
     * Section 4) - an unreachable campaign-service, an OPEN circuit breaker, or a genuinely
     * ineligible decision all leave the item priced at the undiscounted {@code monthlyFee}, exactly
     * as before this feature.
     */
    private PricedItem priceItem(UUID customerId, OrderItemRequest item, TariffClientResponse tariff) {
        CampaignValidationResponse validation =
                campaignServiceClient.validate(customerId, tariff.code(), item.campaignCode());

        if (!validation.eligible()) {
            return new PricedItem(tariff, tariff.monthlyFee(), null, null);
        }

        BigDecimal discountedPrice = applyCampaignDiscount(
                tariff.monthlyFee(), validation.discountType(), validation.discountValue());
        // campaignCode is recorded only when the caller explicitly requested it; when
        // campaign-service auto-resolved the best match, only campaignId is known here (see
        // OrderItem's class javadoc) - still sufficient for Feature 21.4's redemption correlation.
        return new PricedItem(tariff, discountedPrice, validation.campaignId(), item.campaignCode());
    }

    /**
     * {@code PERCENTAGE}: {@code monthlyFee * (1 - discountValue/100)}; {@code FIXED_AMOUNT}:
     * {@code monthlyFee - discountValue} - both floored at zero (ADR-027 Decision Section 4, Feature
     * 21.3.3). An unrecognised discount type defensively falls back to the undiscounted fee rather
     * than failing order creation.
     */
    private static BigDecimal applyCampaignDiscount(BigDecimal monthlyFee, String discountType,
                                                      BigDecimal discountValue) {
        BigDecimal discounted;
        if (DISCOUNT_TYPE_PERCENTAGE.equals(discountType)) {
            BigDecimal factor = BigDecimal.ONE.subtract(
                    discountValue.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
            discounted = monthlyFee.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        } else if (DISCOUNT_TYPE_FIXED_AMOUNT.equals(discountType)) {
            discounted = monthlyFee.subtract(discountValue).setScale(2, RoundingMode.HALF_UP);
        } else {
            return monthlyFee;
        }
        return discounted.max(BigDecimal.ZERO);
    }
}
