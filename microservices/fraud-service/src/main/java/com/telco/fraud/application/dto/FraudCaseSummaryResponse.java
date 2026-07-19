package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudCase;

import java.time.Instant;
import java.util.UUID;

/**
 * List-row view of a {@link FraudCase} (Feature 23.3.1). Domain entities are never exposed directly
 * (ADR-015). {@code signalCount} is the number of {@link com.telco.fraud.domain.FraudSignal}s grouped
 * into the case; the full signal detail is on {@link FraudCaseDetailResponse} from the single-case
 * view. {@code status} serializes the {@link com.telco.fraud.domain.FraudCaseStatus} enum name.
 */
public record FraudCaseSummaryResponse(
        UUID id,
        UUID customerId,
        String status,
        Instant openedAt,
        Instant resolvedAt,
        String resolvedBy,
        int signalCount
) {

    /**
     * Maps a {@link FraudCase} to its summary DTO. {@code getSignalIds()} is read (its size copied)
     * inside the caller's live read transaction; the mapping never leaks the domain entity or a
     * lazily-backed view into the Jackson serialization phase (see {@code docs/tasks/lessons.md}
     * 2026-07-06). {@code signal_ids} is a native {@code uuid[]} column, not a lazy collection, but
     * copying the size here keeps the same safe pattern.
     */
    public static FraudCaseSummaryResponse from(FraudCase fraudCase) {
        return new FraudCaseSummaryResponse(
                fraudCase.getId(),
                fraudCase.getCustomerId(),
                fraudCase.getStatus().name(),
                fraudCase.getOpenedAt(),
                fraudCase.getResolvedAt(),
                fraudCase.getResolvedBy(),
                fraudCase.getSignalIds().size());
    }
}
