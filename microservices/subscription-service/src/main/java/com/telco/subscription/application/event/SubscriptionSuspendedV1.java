package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code subscription.suspended.v1}
 * (ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../subscription-suspended.avsc} field-for-field; the
 * schema-compat gate ({@link com.telco.subscription.SubscriptionEventSchemaCompatTest}) fails the
 * build on any drift. {@code reason} drives downstream dunning/notification (e.g. NON_PAYMENT).
 * {@code suspendedAt} is epoch milliseconds (UTC) to match the Avro {@code long} /
 * {@code timestamp-millis} logical type.
 */
public record SubscriptionSuspendedV1(
        String subscriptionId,
        String customerId,
        String reason,
        long suspendedAt
) implements Event {
}
