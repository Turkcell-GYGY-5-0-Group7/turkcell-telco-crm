package com.telco.fraud.application.dto;

import com.telco.fraud.domain.FraudSignal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read view of a {@link FraudSignal} linked to a case (Feature 23.3.1). Domain entities are never
 * exposed directly (ADR-015). {@code sourceSignalIds} are the contributing
 * {@link com.telco.fraud.domain.MsisdnLifecycleSignal} ids that triggered this rule hit, surfaced for
 * agent audit/investigation traceability (design-note.md Section 6). {@code ruleCode}/{@code severity}
 * serialize their enum names.
 */
public record FraudSignalResponse(
        UUID id,
        String ruleCode,
        UUID customerId,
        String msisdn,
        UUID subscriptionId,
        String severity,
        Instant triggeredAt,
        List<UUID> sourceSignalIds
) {

    /**
     * Maps a {@link FraudSignal} to its response DTO. {@code sourceSignalIds} is eagerly copied into a
     * new {@link ArrayList} here, inside the handler's live read transaction, because Jackson
     * serialization of the HTTP response runs later, outside the session/transaction boundary
     * (LazyInitializationException fix pattern, {@code docs/tasks/lessons.md} 2026-07-06).
     */
    public static FraudSignalResponse from(FraudSignal signal) {
        return new FraudSignalResponse(
                signal.getId(),
                signal.getRuleCode().name(),
                signal.getCustomerId(),
                signal.getMsisdn(),
                signal.getSubscriptionId(),
                signal.getSeverity().name(),
                signal.getTriggeredAt(),
                new ArrayList<>(signal.getSourceSignalIds()));
    }
}
