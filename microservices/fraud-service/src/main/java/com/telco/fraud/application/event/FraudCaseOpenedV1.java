package com.telco.fraud.application.event;

import com.telco.platform.cqrs.Event;

import java.util.List;

/**
 * Versioned event payload published to the outbox as {@code fraud.case-opened.v1} (ADR-009, ADR-019,
 * ADR-029 Section 5). Emitted when related signals escalate into an actionable case; ticket-service
 * (auto-ticket) and notification-service (ops alert) consume it - wired in Feature 23.4.
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../fraud-case-opened.avsc} field-for-field; the schema-compat gate
 * ({@link com.telco.fraud.FraudEventSchemaCompatTest}) fails the build on any drift. {@code signalIds}
 * maps to the Avro {@code array} of string; {@code highestSeverity} serializes the {@code FraudSeverity}
 * enum name; {@code openedAt} is epoch milliseconds (UTC) to match the Avro {@code long} /
 * {@code timestamp-millis} logical type.
 *
 * <p>The outbox publish call site is added by Feature 23.2 ({@code EscalateFraudCaseCommandHandler});
 * this class only defines the contract the handler will populate.
 */
public record FraudCaseOpenedV1(
        String caseId,
        String customerId,
        List<String> signalIds,
        long openedAt,
        String highestSeverity
) implements Event {
}
