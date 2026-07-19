package com.telco.order.application.handler;

import com.telco.order.application.AddonPurchaseEventPublisher;
import com.telco.order.application.AuditLogWriter;
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
import java.util.UUID;

/**
 * Fulfills an order after {@code subscription.activated.v1} (saga step SUBSCRIPTION_ACTIVATED,
 * status FULFILLED, terminal success).
 *
 * <p>Check-then-act: only a CONFIRMED order is fulfilled (CONFIRMED -&gt; FULFILLED) and saga_state
 * advanced. If the order is already FULFILLED (or CANCELLED/FAILED, a terminal state the
 * compensation path already resolved) the call is a no-op, so saga redelivery is idempotent without
 * throwing. No order domain event is published: fulfill is a local state change (AC-01).
 *
 * <p><b>PENDING is a TRANSIENT race, not a terminal no-op (bug found via live acceptance testing,
 * 2026-07-06):</b> {@code payment.completed.v1} (order-service {@code PaymentCompletedEventConsumer})
 * and {@code subscription.activated.v1} (this saga step) are independent Kafka topics/consumer
 * groups with no ordering guarantee between them - subscription-service can activate the
 * subscription and publish {@code subscription.activated.v1} before order-service has even processed
 * the {@code payment.completed.v1} that confirmed the same order (observed gap: as little as ~1-2s).
 * Silently treating that as a no-op (as every OTHER non-CONFIRMED status correctly is) permanently
 * loses the fulfillment: nothing else ever re-attempts it, and the order is stuck at CONFIRMED
 * forever once payment does confirm. PENDING therefore re-throws so Spring Kafka's default retry
 * redelivers this same message shortly after - by then order confirmation has normally already
 * caught up - matching the TRANSIENT/TERMINAL split already used by
 * {@code subscription-service PaymentCompletedEventConsumer}.
 */
@Component
public class FulfillOrderCommandHandler implements CommandHandler<FulfillOrderCommand, OrderResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FulfillOrderCommandHandler.class);

    private final OrderRepository orderRepository;
    private final SagaStateRepository sagaStateRepository;
    private final AuditLogWriter auditLogWriter;
    private final AddonPurchaseEventPublisher addonPurchaseEventPublisher;

    public FulfillOrderCommandHandler(OrderRepository orderRepository,
                                      SagaStateRepository sagaStateRepository,
                                      AuditLogWriter auditLogWriter,
                                      AddonPurchaseEventPublisher addonPurchaseEventPublisher) {
        this.orderRepository = orderRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.auditLogWriter = auditLogWriter;
        this.addonPurchaseEventPublisher = addonPurchaseEventPublisher;
    }

    @Override
    public OrderResponse handle(FulfillOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + command.orderId(),
                        Map.of("orderId", command.orderId().toString())));

        // Check-then-act, split by whether the non-CONFIRMED status is TRANSIENT or TERMINAL.
        if (order.getStatus() == OrderStatus.PENDING) {
            // TRANSIENT: payment.completed.v1 has not been processed by this service yet, even
            // though subscription-service already activated the subscription. Re-throw so Kafka
            // redelivers this message shortly - see class javadoc.
            LOGGER.info("Order {} still PENDING (payment.completed.v1 not yet processed); "
                    + "retrying fulfill on redelivery", order.getId());
            throw new IllegalStateException(
                    "Order " + order.getId() + " not yet CONFIRMED - payment.completed.v1 has not "
                            + "caught up with subscription.activated.v1; retry on redelivery");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            // TERMINAL: FULFILLED (already done, redelivery), or CANCELLED/FAILED (saga already
            // compensated - a late activation cannot un-cancel it). Both are safe, silent no-ops.
            LOGGER.info("Skipping fulfill: order {} in status {} (not CONFIRMED)",
                    order.getId(), order.getStatus());
            return OrderResponse.from(order);
        }

        order.fulfill();
        orderRepository.save(order);

        // Addons bundled into the order top up the just-activated subscription: one
        // addon.purchased.v1 per ADDON item, in the same transaction as the FULFILLED transition
        // (Feature 24.3, design-note D1). No-op for orders without ADDON items.
        addonPurchaseEventPublisher.publishFor(order,
                command.subscriptionId() == null ? null : UUID.fromString(command.subscriptionId()));

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.advance("SUBSCRIPTION_ACTIVATED", "FULFILLED",
                    command.subscriptionId() == null ? null
                            : "{\"subscriptionId\":\"" + command.subscriptionId() + "\"}");
            sagaStateRepository.save(saga);
        });

        auditLogWriter.log("ORDER_FULFILLED", "Order", order.getId().toString(),
                command.subscriptionId() != null ? Map.of("subscriptionId", command.subscriptionId()) : Map.of());

        LOGGER.info("Order {} fulfilled (saga SUBSCRIPTION_ACTIVATED/FULFILLED)", order.getId());
        return OrderResponse.from(order);
    }
}
