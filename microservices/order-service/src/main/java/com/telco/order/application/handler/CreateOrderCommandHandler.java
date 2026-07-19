package com.telco.order.application.handler;

import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCreatedEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderItemType;
import com.telco.order.domain.model.OrderType;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.AddonSnapshotClientResponse;
import com.telco.order.infrastructure.client.CampaignServiceClient;
import com.telco.order.infrastructure.client.CampaignValidationResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.SubscriptionClientResponse;
import com.telco.order.infrastructure.client.SubscriptionServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a new order: derives the order kind from its items (Sprint 24 Feature 24.2, design-note
 * D1/D2), validates the customer, items and - for ADDON/PLAN_CHANGE orders - the target
 * subscription via downstream services, prices each item (tariff items may carry a campaign
 * discount, Feature 21.3.3; addon items are priced from the catalog addon snapshot), persists the
 * order aggregate, initialises the saga state, and emits {@code order.created.v1} via the outbox.
 *
 * <p><b>Order-kind derivation</b> (persisted on the aggregate so saga consumers branch without
 * re-deriving): every item ADDON -&gt; {@link OrderType#ADDON}; exactly one item, TARIFF, carrying a
 * {@code targetSubscriptionId} -&gt; {@link OrderType#PLAN_CHANGE}; anything else -&gt;
 * {@link OrderType#NEW_LINE}.
 *
 * <p><b>Validation matrix</b> ({@link ValidationException} for malformed shapes,
 * {@link BusinessRuleException} for domain-rule violations):
 * <ul>
 *   <li>NEW_LINE: exactly one TARIFF item ({@code tariffId} required) plus 0..N bundled ADDON items
 *       ({@code productCode} required); {@code targetSubscriptionId} must be absent on every
 *       item.</li>
 *   <li>ADDON: 1..N ADDON items, all carrying the SAME non-null {@code targetSubscriptionId}; the
 *       target subscription must exist, be ACTIVE and belong to the ordering customer.</li>
 *   <li>PLAN_CHANGE: exactly one TARIFF item with {@code tariffId} and
 *       {@code targetSubscriptionId}; the target subscription must exist, be ACTIVE, belong to the
 *       ordering customer, and its current tariff must differ from the requested one.</li>
 *   <li>{@code campaignCode} on a non-TARIFF item is always a validation error (campaign
 *       eligibility is tariff-scoped, ADR-027).</li>
 * </ul>
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
    private static final String SUBSCRIPTION_STATUS_ACTIVE = "ACTIVE";

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final CustomerServiceClient customerServiceClient;
    private final ProductCatalogServiceClient productCatalogServiceClient;
    private final CampaignServiceClient campaignServiceClient;
    private final SubscriptionServiceClient subscriptionServiceClient;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CreateOrderCommandHandler(OrderRepository orderRepository,
                                     SagaStateRepository sagaStateRepository,
                                     CustomerServiceClient customerServiceClient,
                                     ProductCatalogServiceClient productCatalogServiceClient,
                                     CampaignServiceClient campaignServiceClient,
                                     SubscriptionServiceClient subscriptionServiceClient,
                                     OutboxService outboxService,
                                     AuditLogWriter auditLogWriter) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.customerServiceClient = customerServiceClient;
        this.productCatalogServiceClient = productCatalogServiceClient;
        this.campaignServiceClient = campaignServiceClient;
        this.subscriptionServiceClient = subscriptionServiceClient;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    /**
     * A priced line: exactly one of {@code tariff} (with the campaign discount decision) or
     * {@code addon} is present, matching the item's type.
     */
    private record PricedItem(OrderItemRequest request, TariffClientResponse tariff,
                              AddonSnapshotClientResponse addon, BigDecimal unitPrice,
                              UUID campaignId, String campaignCode) {
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

        // Derive the order kind from the item shapes, then validate the matrix (cheap, no I/O).
        OrderType orderType = deriveOrderType(command.items());
        validateItemShapes(orderType, command.items());

        // Price every item: tariff items via catalog + fail-open campaign check (unchanged
        // pre-24.2 path), addon items via the catalog addon snapshot (fail-closed).
        List<PricedItem> pricedItems = new ArrayList<>();
        for (OrderItemRequest item : command.items()) {
            if (item.effectiveItemType() == OrderItemType.TARIFF) {
                TariffClientResponse tariff = productCatalogServiceClient.getTariff(item.tariffId());
                pricedItems.add(priceTariffItem(command.customerId(), item, tariff));
            } else {
                AddonSnapshotClientResponse addon =
                        productCatalogServiceClient.getAddonSnapshot(item.productCode());
                pricedItems.add(new PricedItem(item, null, addon, addon.price(), null, null));
            }
        }

        // ADDON/PLAN_CHANGE orders target an existing subscription: it must exist, be ACTIVE and be
        // owned by the ordering customer; a PLAN_CHANGE must actually change the tariff.
        validateTargetSubscription(orderType, command.customerId(), pricedItems);

        // Calculate total amount from the (possibly discounted) unit prices.
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PricedItem priced : pricedItems) {
            BigDecimal lineTotal = priced.unitPrice()
                    .multiply(BigDecimal.valueOf(priced.request().quantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // Create and persist the order aggregate.
        Order order = Order.create(command.customerId(), command.idempotencyKey(), totalAmount,
                command.userId(), orderType);
        for (PricedItem priced : pricedItems) {
            OrderItemRequest itemReq = priced.request();
            if (itemReq.effectiveItemType() == OrderItemType.TARIFF) {
                order.addTariffItem(
                        itemReq.tariffId(),
                        priced.tariff().code(),
                        priced.tariff().version(),
                        priced.tariff().name(),
                        priced.unitPrice(),
                        itemReq.quantity(),
                        priced.campaignId(),
                        priced.campaignCode(),
                        itemReq.targetSubscriptionId()
                );
            } else {
                order.addAddonItem(
                        priced.addon().code(),
                        priced.addon().name(),
                        priced.addon().type(),
                        priced.addon().currency(),
                        priced.unitPrice(),
                        itemReq.quantity(),
                        itemReq.targetSubscriptionId(),
                        priced.addon().dataMb(),
                        priced.addon().voiceMinutes(),
                        priced.addon().smsCount()
                );
            }
        }
        orderRepository.save(order);

        // Initialise saga state for this order.
        SagaState sagaState = SagaState.init(
                order.getId(), "ORDER_CREATED", "PENDING", null);
        sagaStateRepository.save(sagaState);

        // Build event item payloads. Built from the priced lines (not the persisted items) because
        // the contract-mandatory non-null tariffId/tariffName fields generalize to the catalog
        // product snapshot for ADDON items (addon id/name), which the persisted item does not carry.
        List<OrderCreatedEvent.OrderItemPayload> eventItems = pricedItems.stream()
                .map(priced -> {
                    OrderItemRequest itemReq = priced.request();
                    boolean isTariff = itemReq.effectiveItemType() == OrderItemType.TARIFF;
                    return new OrderCreatedEvent.OrderItemPayload(
                            isTariff ? itemReq.tariffId().toString() : priced.addon().id().toString(),
                            isTariff ? priced.tariff().name() : priced.addon().name(),
                            priced.unitPrice(),
                            itemReq.quantity(),
                            priced.campaignId() == null ? null : priced.campaignId().toString(),
                            itemReq.effectiveItemType().name(),
                            isTariff ? null : priced.addon().code(),
                            itemReq.targetSubscriptionId() == null
                                    ? null : itemReq.targetSubscriptionId().toString()
                    );
                })
                .toList();

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
                        Instant.now().toString()
                )
        );

        auditLogWriter.log("ORDER_CREATED", "Order", order.getId().toString(),
                Map.of("customerId", command.customerId().toString(),
                        "orderType", orderType.name(),
                        "totalAmount", order.getTotalAmount().toPlainString()));

        return OrderResponse.from(order);
    }

    /**
     * Derives the order kind from the item shapes (design-note D1/D2): every item ADDON -&gt;
     * ADDON; exactly one TARIFF item carrying a {@code targetSubscriptionId} -&gt; PLAN_CHANGE;
     * anything else -&gt; NEW_LINE (whose matrix then rejects malformed shapes).
     */
    private static OrderType deriveOrderType(List<OrderItemRequest> items) {
        boolean allAddon = items.stream()
                .allMatch(item -> item.effectiveItemType() == OrderItemType.ADDON);
        if (allAddon) {
            return OrderType.ADDON;
        }
        if (items.size() == 1
                && items.get(0).effectiveItemType() == OrderItemType.TARIFF
                && items.get(0).targetSubscriptionId() != null) {
            return OrderType.PLAN_CHANGE;
        }
        return OrderType.NEW_LINE;
    }

    /** Enforces the per-kind item shape rules that need no downstream I/O. */
    private static void validateItemShapes(OrderType orderType, List<OrderItemRequest> items) {
        for (int i = 0; i < items.size(); i++) {
            OrderItemRequest item = items.get(i);
            if (item.effectiveItemType() == OrderItemType.TARIFF) {
                if (item.tariffId() == null) {
                    throw new ValidationException("TARIFF items require a tariffId",
                            Map.of("itemIndex", i));
                }
            } else {
                if (item.productCode() == null || item.productCode().isBlank()) {
                    throw new ValidationException("ADDON items require a productCode",
                            Map.of("itemIndex", i));
                }
                if (item.campaignCode() != null) {
                    throw new ValidationException(
                            "campaignCode is only supported on TARIFF items (campaign eligibility "
                                    + "is tariff-scoped)",
                            Map.of("itemIndex", i, "campaignCode", item.campaignCode()));
                }
            }
        }

        switch (orderType) {
            case NEW_LINE -> {
                long tariffCount = items.stream()
                        .filter(item -> item.effectiveItemType() == OrderItemType.TARIFF)
                        .count();
                if (tariffCount != 1) {
                    throw new ValidationException(
                            "A NEW_LINE order must contain exactly one TARIFF item",
                            Map.of("tariffItemCount", tariffCount));
                }
                if (items.stream().anyMatch(item -> item.targetSubscriptionId() != null)) {
                    throw new ValidationException(
                            "targetSubscriptionId is not allowed on NEW_LINE order items "
                                    + "(use an all-ADDON order for an existing subscription)",
                            Map.of("orderType", orderType.name()));
                }
            }
            case ADDON -> {
                UUID target = items.get(0).targetSubscriptionId();
                if (target == null) {
                    throw new ValidationException(
                            "A standalone ADDON order requires a targetSubscriptionId on every item",
                            Map.of("orderType", orderType.name()));
                }
                boolean sameTarget = items.stream()
                        .allMatch(item -> Objects.equals(target, item.targetSubscriptionId()));
                if (!sameTarget) {
                    throw new ValidationException(
                            "All items of an ADDON order must target the same subscription",
                            Map.of("orderType", orderType.name()));
                }
            }
            case PLAN_CHANGE -> {
                // By derivation: exactly one TARIFF item with tariffId and targetSubscriptionId.
            }
        }
    }

    /**
     * ADDON/PLAN_CHANGE orders: one fail-closed hop to subscription-service (all items share the
     * same target by matrix construction) verifying the subscription exists (404 propagates), is
     * ACTIVE and belongs to the ordering customer; PLAN_CHANGE additionally requires the requested
     * tariff to differ from the subscription's current one (design-note D2). NEW_LINE orders make
     * no hop.
     */
    private void validateTargetSubscription(OrderType orderType, UUID customerId,
                                            List<PricedItem> pricedItems) {
        if (orderType == OrderType.NEW_LINE) {
            return;
        }
        UUID targetSubscriptionId = pricedItems.get(0).request().targetSubscriptionId();
        SubscriptionClientResponse subscription =
                subscriptionServiceClient.getSubscription(targetSubscriptionId);

        if (!SUBSCRIPTION_STATUS_ACTIVE.equals(subscription.status())) {
            throw new BusinessRuleException(
                    "Target subscription " + targetSubscriptionId + " is not ACTIVE (status: "
                            + subscription.status() + ")");
        }
        if (!customerId.equals(subscription.customerId())) {
            throw new BusinessRuleException(
                    "Target subscription " + targetSubscriptionId
                            + " does not belong to the ordering customer");
        }
        if (orderType == OrderType.PLAN_CHANGE) {
            String requestedTariffCode = pricedItems.get(0).tariff().code();
            if (requestedTariffCode != null && requestedTariffCode.equals(subscription.tariffCode())) {
                throw new BusinessRuleException(
                        "PLAN_CHANGE order must change the tariff: subscription "
                                + targetSubscriptionId + " is already on tariff " + requestedTariffCode);
            }
        }
    }

    /**
     * Asks campaign-service (via the fail-open {@link CampaignServiceClient}) whether a discount
     * applies to this tariff line item and computes the resulting {@code unitPrice}. Never throws:
     * {@code CampaignServiceClient.validate(...)} itself never propagates a failure (ADR-027 Decision
     * Section 4) - an unreachable campaign-service, an OPEN circuit breaker, or a genuinely
     * ineligible decision all leave the item priced at the undiscounted {@code monthlyFee}, exactly
     * as before this feature.
     */
    private PricedItem priceTariffItem(UUID customerId, OrderItemRequest item, TariffClientResponse tariff) {
        CampaignValidationResponse validation =
                campaignServiceClient.validate(customerId, tariff.code(), item.campaignCode());

        if (!validation.eligible()) {
            return new PricedItem(item, tariff, null, tariff.monthlyFee(), null, null);
        }

        BigDecimal discountedPrice = applyCampaignDiscount(
                tariff.monthlyFee(), validation.discountType(), validation.discountValue());
        // campaignCode is recorded only when the caller explicitly requested it; when
        // campaign-service auto-resolved the best match, only campaignId is known here (see
        // OrderItem's class javadoc) - still sufficient for Feature 21.4's redemption correlation.
        return new PricedItem(item, tariff, null, discountedPrice, validation.campaignId(),
                item.campaignCode());
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
