package com.telco.order.application.handler;

import com.telco.order.application.command.FulfillOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderStatus;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fulfills an order after {@code subscription.activated.v1} (saga step SUBSCRIPTION_ACTIVATED,
 * status FULFILLED, terminal success).
 *
 * <p>Check-then-act: only a CONFIRMED order is fulfilled (CONFIRMED -&gt; FULFILLED) and saga_state
 * advanced. If the order is already FULFILLED (or otherwise no longer CONFIRMED) the call is a
 * no-op, so saga redelivery is idempotent without throwing. No order domain event is published:
 * fulfill is a local state change (AC-01).
 */
@Component
public class FulfillOrderCommandHandler implements CommandHandler<FulfillOrderCommand, OrderResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FulfillOrderCommandHandler.class);

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;

    public FulfillOrderCommandHandler(OrderRepository orderRepository,
                                      SagaStateRepository sagaStateRepository) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
    }

    @Override
    public OrderResponse handle(FulfillOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + command.orderId(),
                        Map.of("orderId", command.orderId().toString())));

        // Check-then-act: only a CONFIRMED order can be fulfilled. Already-FULFILLED (or other) is a no-op.
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            LOGGER.info("Skipping fulfill: order {} in status {} (not CONFIRMED)",
                    order.getId(), order.getStatus());
            return OrderResponse.from(order);
        }

        order.fulfill();
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.advance("SUBSCRIPTION_ACTIVATED", "FULFILLED",
                    command.subscriptionId() == null ? null
                            : "{\"subscriptionId\":\"" + command.subscriptionId() + "\"}");
            sagaStateRepository.save(saga);
        });

        LOGGER.info("Order {} fulfilled (saga SUBSCRIPTION_ACTIVATED/FULFILLED)", order.getId());
        return OrderResponse.from(order);
    }
}
