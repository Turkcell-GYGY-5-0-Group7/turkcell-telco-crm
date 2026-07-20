package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code subscription.tariff-changed.v1}
 * (FR-09, ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../subscription-tariff-changed.avsc} field-for-field; the
 * schema-compat gate ({@link com.telco.subscription.SubscriptionEventSchemaCompatTest}) fails the
 * build on any drift. {@code changedAt} is epoch milliseconds (UTC) to match the Avro
 * {@code long} / {@code timestamp-millis} logical type.
 */
public record SubscriptionTariffChangedV1(
        String subscriptionId,
        String customerId,
        String orderId,
        String oldTariffCode,
        String newTariffCode,
        long changedAt
) implements Event {
}
