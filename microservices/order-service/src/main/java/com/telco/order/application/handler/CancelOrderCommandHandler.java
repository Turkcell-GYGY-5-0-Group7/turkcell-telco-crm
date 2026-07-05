package com.telco.order.application.handler;

import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.CancelOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCancelledEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Cancels an order. Enforces ownership: SUBSCRIBER callers may only cancel their own orders; system
 * callers ({@code callerIsAdmin=true}) bypass the guard for saga compensation. The legal source
 * states (PENDING or CONFIRMED -> CANCELLED) are enforced by {@link Order#cancel()}; illegal
 * transitions raise {@link com.telco.platform.common.exception.BusinessRuleException} (-> 422).
 * Publishes {@code order.cancelled.v1} and advances saga_state to COMPENSATED/CANCELLED.
 */
@Component
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, OrderResponse> {

    private static final String OUTBOX_AGGREGATE_TYPE = "order";
    private static final String EVENT_TYPE = "order.cancelled.v1";

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CancelOrderCommandHandler(OrderRepository orderRepository,
                                     SagaStateRepository sagaStateRepository,
                                     OutboxService outboxService,
                                     AuditLogWriter auditLogWriter) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public OrderResponse handle(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + command.orderId(),
                        Map.of("orderId", command.orderId().toString())));

        if (!command.callerIsAdmin() && !order.getUserId().equals(command.callerUserId())) {
            throw new AccessDeniedException("Order does not belong to caller");
        }

        order.cancel();
        orderRepository.save(order);

        // Terminate the saga: cancellation is the compensated/terminal path.
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

        auditLogWriter.log("ORDER_CANCELLED", "Order", order.getId().toString(),
                command.reason() != null ? Map.of("reason", command.reason()) : Map.of());

        return OrderResponse.from(order);
    }
}
