package com.telco.order.application.handler;

import com.telco.order.application.AddonPurchaseEventPublisher;
import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.ConfirmOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderStatus;
import com.telco.order.domain.model.OrderType;
import com.telco.order.domain.model.SagaState;
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
 * Confirms an order after {@code payment.completed.v1} (saga step PAYMENT_COMPLETED, status PAID).
 *
 * <p>Check-then-act: a PENDING order is confirmed (PENDING -&gt; CONFIRMED) and saga_state advanced.
 * If the order is already CONFIRMED or further (FULFILLED / CANCELLED / FAILED) the call is a
 * no-op, so saga redelivery is idempotent without throwing. No order domain event is published:
 * confirm is a local state change (AC-01).
 *
 * <p><b>Standalone ADDON orders confirm AND fulfill here in one flow</b> (Sprint 24 Feature 24.3,
 * design-note D1): they have no activation leg - subscription-service deliberately ignores their
 * {@code payment.completed.v1} - so nothing else would ever fulfill them. Payment success is the
 * terminal condition: the order goes PENDING -&gt; CONFIRMED -&gt; FULFILLED atomically and one
 * {@code addon.purchased.v1} per item (carrying its own {@code targetSubscriptionId}) is published
 * through the outbox in the same transaction. Redelivery safety is unchanged: the inbox dedups the
 * message and the PENDING status gate makes a second pass a no-op.
 */
@Component
public class ConfirmOrderCommandHandler implements CommandHandler<ConfirmOrderCommand, OrderResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmOrderCommandHandler.class);

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final AuditLogWriter auditLogWriter;
    private final AddonPurchaseEventPublisher addonPurchaseEventPublisher;

    public ConfirmOrderCommandHandler(OrderRepository orderRepository,
                                      SagaStateRepository sagaStateRepository,
                                      AuditLogWriter auditLogWriter,
                                      AddonPurchaseEventPublisher addonPurchaseEventPublisher) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.auditLogWriter = auditLogWriter;
        this.addonPurchaseEventPublisher = addonPurchaseEventPublisher;
    }

    @Override
    public OrderResponse handle(ConfirmOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + command.orderId(),
                        Map.of("orderId", command.orderId().toString())));

        // Check-then-act: only a PENDING order needs confirming. Already-advanced orders are a no-op.
        if (order.getStatus() != OrderStatus.PENDING) {
            LOGGER.info("Skipping confirm: order {} already in status {}",
                    order.getId(), order.getStatus());
            return OrderResponse.from(order);
        }

        order.confirm();
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.advance("PAYMENT_COMPLETED", "PAID",
                    command.paymentId() == null ? null : "{\"paymentId\":\"" + command.paymentId() + "\"}");
            sagaStateRepository.save(saga);
        });

        auditLogWriter.log("ORDER_CONFIRMED", "Order", order.getId().toString(),
                command.paymentId() != null ? Map.of("paymentId", command.paymentId()) : Map.of());

        LOGGER.info("Order {} confirmed (saga PAYMENT_COMPLETED/PAID)", order.getId());

        // Standalone ADDON orders have no activation leg: payment success is terminal, so fulfill
        // in the same transaction and publish one addon.purchased.v1 per item (design-note D1).
        if (order.getOrderType() == OrderType.ADDON) {
            order.fulfill();
            orderRepository.save(order);

            addonPurchaseEventPublisher.publishFor(order, null);

            sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
                saga.advance("ADDON_FULFILLED", "FULFILLED", null);
                sagaStateRepository.save(saga);
            });

            auditLogWriter.log("ORDER_FULFILLED", "Order", order.getId().toString(),
                    Map.of("orderType", OrderType.ADDON.name()));

            LOGGER.info("Standalone ADDON order {} fulfilled (saga ADDON_FULFILLED/FULFILLED)",
                    order.getId());
        }

        return OrderResponse.from(order);
    }
}
