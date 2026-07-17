package com.telco.dispute.application.event;

/** {@code dispute.evidence-submitted.v1} (ADR-028 Section 6). */
public record DisputeEvidenceSubmittedEvent(
        String disputeId,
        String evidenceId,
        String submittedBy,
        String submittedAt
) {
}
