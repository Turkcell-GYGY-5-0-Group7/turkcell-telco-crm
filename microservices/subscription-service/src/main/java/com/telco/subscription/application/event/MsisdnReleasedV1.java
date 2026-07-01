package com.telco.subscription.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code msisdn.released.v1} (ADR-009, ADR-019).
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../msisdn-released.avsc} field-for-field; the schema-compat gate
 * (MsisdnEventSchemaCompatTest) fails the build on any drift. {@code releasedAt} is epoch
 * milliseconds (UTC) to match the Avro {@code long} / {@code timestamp-millis} logical type.
 */
public record MsisdnReleasedV1(
        String msisdn,
        String subscriptionId,
        long releasedAt
) implements Event {
}
