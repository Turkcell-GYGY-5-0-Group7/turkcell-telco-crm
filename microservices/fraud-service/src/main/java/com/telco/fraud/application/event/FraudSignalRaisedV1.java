package com.telco.fraud.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code fraud.signal-raised.v1} (ADR-009,
 * ADR-019, ADR-029 Section 5). Informational: emitted for every fraud-rule hit.
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../fraud-signal-raised.avsc} field-for-field; the schema-compat
 * gate ({@link com.telco.fraud.FraudEventSchemaCompatTest}) fails the build on any drift.
 * {@code ruleCode} and {@code severity} serialize the {@code FraudRuleCode}/{@code FraudSeverity}
 * enum names; {@code triggeredAt} is epoch milliseconds (UTC) to match the Avro {@code long} /
 * {@code timestamp-millis} logical type. {@code msisdn} and {@code subscriptionId} are nullable.
 *
 * <p>The outbox publish call site is added by Feature 23.2 (the rule-evaluation handlers); this class
 * only defines the contract the handlers will populate.
 */
public record FraudSignalRaisedV1(
        String signalId,
        String ruleCode,
        String customerId,
        String msisdn,
        String subscriptionId,
        String severity,
        long triggeredAt
) implements Event {
}
