package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code subscription.activated.v1}
 * (ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../subscription-activated.avsc} field-for-field; the
 * schema-compat gate ({@link com.telco.subscription.SubscriptionEventSchemaCompatTest}) fails the
 * build on any drift. UUIDs serialize as String; {@code activatedAt} is epoch milliseconds (UTC) to
 * match the Avro {@code long} / {@code timestamp-millis} logical type.
 */
public record SubscriptionActivatedV1(
        String subscriptionId,
        String customerId,
        String msisdn,
        String tariffCode,
        long activatedAt,
        String orderId
) implements Event {
}
