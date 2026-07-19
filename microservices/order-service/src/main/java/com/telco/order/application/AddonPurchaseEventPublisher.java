package com.telco.order.application;

import com.telco.order.application.event.AddonPurchasedEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderItem;
import com.telco.order.domain.model.OrderItemType;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes one {@code addon.purchased.v1} per ADDON item of a just-fulfilled order through the
 * transactional outbox (Sprint 24 Feature 24.3, design-note D1/D3).
 *
 * <p>Shared by the two fulfillment legs: {@code FulfillOrderCommandHandler} (addons bundled into a
 * NEW_LINE order - {@code subscriptionId} comes from the {@code subscription.activated.v1}
 * payload) and {@code ConfirmOrderCommandHandler}'s standalone-ADDON branch ({@code subscriptionId}
 * comes from each item's {@code targetSubscriptionId}). Callers invoke this INSIDE the same
 * transaction as the FULFILLED transition, and only when the transition actually happened, so the
 * events commit atomically with the state change and are never re-published on redelivery no-ops
 * (ADR-009; inbox dedup on the consumer side is keyed on the order-item aggregate_id).
 */
@Component
public class AddonPurchaseEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonPurchaseEventPublisher.class);
    private static final String OUTBOX_AGGREGATE_TYPE = "addon";
    private static final String EVENT_TYPE = "addon.purchased.v1";

    private final OutboxService outboxService;

    public AddonPurchaseEventPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * Publishes one event per ADDON item of {@code order}. {@code activatedSubscriptionId} is the
     * subscription the bundled addons attach to (from the activation payload) and may be null for
     * standalone ADDON orders, where every item carries its own {@code targetSubscriptionId}.
     * Orders without ADDON items (plain NEW_LINE, PLAN_CHANGE) are a no-op.
     */
    public void publishFor(Order order, UUID activatedSubscriptionId) {
        Instant occurredAt = Instant.now();
        for (OrderItem item : order.getItems()) {
            if (item.getItemType() != OrderItemType.ADDON) {
                continue;
            }
            UUID subscriptionId = item.getTargetSubscriptionId() != null
                    ? item.getTargetSubscriptionId() : activatedSubscriptionId;
            if (subscriptionId == null) {
                // Contract violation: a bundled addon fulfillment without an activation
                // subscriptionId (subscription.activated.v1 declares it non-null). Failing here
                // would poison-loop the whole fulfillment; the order still fulfills and the gap
                // is loudly visible in logs/audit instead.
                LOGGER.error("No subscriptionId for ADDON item {} of order {}; "
                        + "skipping addon.purchased.v1 publish", item.getId(), order.getId());
                continue;
            }
            outboxService.publish(
                    OUTBOX_AGGREGATE_TYPE,
                    item.getId().toString(),
                    EVENT_TYPE,
                    new AddonPurchasedEvent(
                            order.getId().toString(),
                            order.getCustomerId().toString(),
                            subscriptionId.toString(),
                            item.getProductCode(),
                            item.getTariffName(),
                            item.getAddonType(),
                            item.getUnitPrice(),
                            item.getCurrency(),
                            item.getQuantity(),
                            item.getAllowanceDataMb(),
                            item.getAllowanceMinutes(),
                            item.getAllowanceSms(),
                            occurredAt.toString()
                    )
            );
            LOGGER.info("addon.purchased.v1 queued for order {} item {} addon {} subscription {}",
                    order.getId(), item.getId(), item.getProductCode(), subscriptionId);
        }
    }
}
