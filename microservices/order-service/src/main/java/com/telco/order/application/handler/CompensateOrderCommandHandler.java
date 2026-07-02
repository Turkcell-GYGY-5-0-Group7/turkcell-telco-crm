package com.telco.order.application.handler;

import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.CompensateOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCancelledEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderStatus;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Compensates the saga by cancelling an order after {@code payment.refunded.v1} (saga step
 * COMPENSATED / status CANCELLED). System actor: no ownership guard. Atomic inbox dedup is provided
 * by {@code InboxBehavior} (the command is an {@code IdempotentRequest} keyed on the Kafka
 * messageId), running inside this handler's transaction.
 *
 * <p>Check-then-act: only a PENDING or CONFIRMED order is cancellable; an already-CANCELLED or
 * terminal (FULFILLED/FAILED) order is a no-op, so a redelivery with a fresh messageId does not throw
 * an illegal-transition exception. Publishes {@code order.cancelled.v1} and advances saga_state.
 */
@Component
public class CompensateOrderCommandHandler implements CommandHandler<CompensateOrderCommand, OrderResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensateOrderCommandHandler.class);
    private static final String OUTBOX_AGGREGATE_TYPE = "order";
    private static final String EVENT_TYPE = "order.cancelled.v1";

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CompensateOrderCommandHandler(OrderRepository orderRepository,
                                         SagaStateRepository sagaStateRepository,
                                         OutboxService outboxService,
                                         AuditLogWriter auditLogWriter) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public OrderResponse handle(CompensateOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + command.orderId(),
                        Map.of("orderId", command.orderId().toString())));

        // Check-then-act: only a PENDING or CONFIRMED order is cancellable. Already-compensated or
        // terminal orders are a no-op so redelivery does not throw an illegal-transition exception.
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
            LOGGER.info("Skipping compensation: order {} already in status {}", order.getId(), status);
            return OrderResponse.from(order);
        }

        order.cancel();
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.advance("COMPENSATED", "CANCELLED",
                    command.reason() == null ? null : "{\"reason\":\"" + command.reason() + "\"}");
            sagaStateRepository.save(saga);
        });

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                order.getId().toString(),
                EVENT_TYPE,
                new OrderCancelledEvent(
                        order.getId().toString(),
                        order.getCustomerId().toString(),
                        command.reason(),
                        Instant.now().toString()
                )
        );

        auditLogWriter.log("ORDER_COMPENSATED", "Order", order.getId().toString(),
                command.reason() != null ? Map.of("reason", command.reason()) : Map.of());

        LOGGER.info("Order {} compensated (saga COMPENSATED/CANCELLED)", order.getId());
        return OrderResponse.from(order);
    }
}
