package com.telco.fraud.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code fraud.case-resolved.v1} (ADR-009,
 * ADR-019, ADR-029 Section 5). Emitted when a fraud case reaches a terminal CONFIRMED/DISMISSED
 * outcome.
 *
 * <p>Field names and order mirror the canonical Avro contract
 * {@code platform-event-contracts/.../fraud-case-resolved.avsc} field-for-field; the schema-compat
 * gate ({@link com.telco.fraud.FraudEventSchemaCompatTest}) fails the build on any drift.
 * {@code status} serializes the {@code FraudCaseStatus} enum name. {@code resolvedAt} (epoch
 * milliseconds, UTC, matching the Avro {@code long} / {@code timestamp-millis} logical type) and
 * {@code resolvedBy} are nullable, so both use boxed reference types.
 *
 * <p>The outbox publish call site is added by Feature 23.3 ({@code ResolveFraudCaseCommandHandler});
 * this class only defines the contract the handler will populate.
 */
public record FraudCaseResolvedV1(
        String caseId,
        String customerId,
        String status,
        Long resolvedAt,
        String resolvedBy
) implements Event {
}
