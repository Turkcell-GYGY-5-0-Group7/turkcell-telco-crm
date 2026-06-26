package com.telco.order.application.handler;

import com.telco.order.application.command.CancelOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.event.OrderCancelledEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Cancels a PENDING order. Enforces ownership: CUSTOMER callers may only cancel their own orders.
 * The PENDING -> CANCELLED state transition is enforced by {@link Order#cancel()}.
 * Publishes {@code order.cancelled.v1}.
 */
@Component
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, OrderResponse> {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_TYPE = "order.cancelled.v1";

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    public CancelOrderCommandHandler(OrderRepository orderRepository, OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
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

        outboxService.publish(
                AGGREGATE_TYPE,
                order.getId().toString(),
                EVENT_TYPE,
                new OrderCancelledEvent(
                        order.getId().toString(),
                        order.getCustomerId().toString(),
                        command.reason(),
                        Instant.now().toString()
                )
        );

        return OrderResponse.from(order);
    }
}
