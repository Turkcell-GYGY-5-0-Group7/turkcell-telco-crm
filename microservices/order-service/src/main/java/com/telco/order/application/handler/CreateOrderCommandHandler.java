package com.telco.order.application.handler;

import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCreatedEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates a new order: validates customer and tariffs via downstream services, persists the order
 * aggregate, initialises the saga state, and emits {@code order.created.v1} via the outbox.
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

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final CustomerServiceClient customerServiceClient;
    private final ProductCatalogServiceClient productCatalogServiceClient;
    private final OutboxService outboxService;

    public CreateOrderCommandHandler(OrderRepository orderRepository,
                                     SagaStateRepository sagaStateRepository,
                                     CustomerServiceClient customerServiceClient,
                                     ProductCatalogServiceClient productCatalogServiceClient,
                                     OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.customerServiceClient = customerServiceClient;
        this.productCatalogServiceClient = productCatalogServiceClient;
        this.outboxService = outboxService;
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

        // Validate each tariff and collect price snapshots.
        List<TariffClientResponse> tariffs = new ArrayList<>();
        for (OrderItemRequest item : command.items()) {
            TariffClientResponse tariff = productCatalogServiceClient.getTariff(item.tariffId());
            tariffs.add(tariff);
        }

        // Calculate total amount from price snapshots.
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < command.items().size(); i++) {
            BigDecimal lineTotal = tariffs.get(i).monthlyFee()
                    .multiply(BigDecimal.valueOf(command.items().get(i).quantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // Create and persist the order aggregate.
        Order order = Order.create(command.customerId(), command.idempotencyKey(), totalAmount, command.userId());
        for (int i = 0; i < command.items().size(); i++) {
            OrderItemRequest itemReq = command.items().get(i);
            TariffClientResponse tariff = tariffs.get(i);
            order.addItem(
                    itemReq.tariffId(),
                    tariff.code(),
                    tariff.version(),
                    tariff.name(),
                    tariff.monthlyFee(),
                    itemReq.quantity()
            );
        }
        orderRepository.save(order);

        // Initialise saga state for this order.
        SagaState sagaState = SagaState.init(
                order.getId(), "ORDER_CREATED", "PENDING", null);
        sagaStateRepository.save(sagaState);

        // Build event item payloads from the order items.
        List<OrderCreatedEvent.OrderItemPayload> eventItems = order.getItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItemPayload(
                        item.getTariffId().toString(),
                        item.getTariffName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
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

        return OrderResponse.from(order);
    }
}
