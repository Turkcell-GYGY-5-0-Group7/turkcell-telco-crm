package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudCase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single-case view of a {@link FraudCase} with its linked {@link com.telco.fraud.domain.FraudSignal}s
 * (Feature 23.3.1). Domain entities are never exposed directly (ADR-015). Each signal carries its
 * contributing {@link com.telco.fraud.domain.MsisdnLifecycleSignal} ids for agent audit/investigation
 * traceability (design-note.md Section 6). {@code status} serializes the
 * {@link com.telco.fraud.domain.FraudCaseStatus} enum name.
 */
public record FraudCaseDetailResponse(
        UUID id,
        UUID customerId,
        String status,
        Instant openedAt,
        Instant resolvedAt,
        String resolvedBy,
        List<FraudSignalResponse> signals
) {

    /**
     * Assembles the detail DTO from a case and its already-loaded signals. The {@code signals} list is
     * built by the query handler from {@link FraudSignalResponse#from} inside the live read
     * transaction, so no lazy state crosses into Jackson serialization ({@code docs/tasks/lessons.md}
     * 2026-07-06).
     */
    public static FraudCaseDetailResponse of(FraudCase fraudCase, List<FraudSignalResponse> signals) {
        return new FraudCaseDetailResponse(
                fraudCase.getId(),
                fraudCase.getCustomerId(),
                fraudCase.getStatus().name(),
                fraudCase.getOpenedAt(),
                fraudCase.getResolvedAt(),
                fraudCase.getResolvedBy(),
                List.copyOf(signals));
    }
}
