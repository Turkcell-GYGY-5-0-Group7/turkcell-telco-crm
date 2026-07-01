package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code subscription.activation-failed.v1}
 * (ADR-009, ADR-019). Producer side only; the saga compensation consumers (payment refund, order
 * cancel) are wired in feature 9.4.3.
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../subscription-activation-failed.avsc} field-for-field; the
 * schema-compat gate ({@link com.telco.subscription.SubscriptionEventSchemaCompatTest}) fails the
 * build on any drift. {@code orderId} is the saga correlation key. {@code subscriptionId} is
 * nullable (Avro union {@code ["null","string"]}) because activation can fail before a subscription
 * id is assigned (e.g. MSISDN pool exhausted). {@code failedAt} is epoch milliseconds (UTC) to match
 * the Avro {@code long} / {@code timestamp-millis} logical type.
 */
public record SubscriptionActivationFailedV1(
        String orderId,
        String customerId,
        String subscriptionId,
        String reason,
        long failedAt
) implements Event {
}
