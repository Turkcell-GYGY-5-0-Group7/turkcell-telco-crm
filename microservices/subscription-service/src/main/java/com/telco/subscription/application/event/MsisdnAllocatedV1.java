package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code msisdn.allocated.v1} (ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../msisdn-allocated.avsc} field-for-field; the schema-compat gate
 * (MsisdnEventSchemaCompatTest) fails the build on any drift. {@code allocatedAt} is epoch
 * milliseconds (UTC) to match the Avro {@code long} / {@code timestamp-millis} logical type.
 */
public record MsisdnAllocatedV1(
        String msisdn,
        String subscriptionId,
        String customerId,
        long allocatedAt
) implements Event {
}
