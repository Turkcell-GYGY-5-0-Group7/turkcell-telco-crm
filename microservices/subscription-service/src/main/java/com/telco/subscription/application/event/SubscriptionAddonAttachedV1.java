package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;

/**
 * Versioned event payload published to the outbox as {@code subscription.addon-attached.v1}
 * (FR-09/FR-22, ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../subscription-addon-attached.avsc} field-for-field; the
 * schema-compat gate ({@link com.telco.subscription.SubscriptionEventSchemaCompatTest}) fails the
 * build on any drift. {@code attachedAt} is epoch milliseconds (UTC).
 */
public record SubscriptionAddonAttachedV1(
        String subscriptionId,
        String customerId,
        String orderId,
        String addonCode,
        String addonType,
        BigDecimal price,
        String currency,
        long attachedAt
) implements Event {
}
