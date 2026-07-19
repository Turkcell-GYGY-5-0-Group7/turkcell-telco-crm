package com.telco.dispute.application.event;

/** {@code dispute.withdrawn.v1} (ADR-028 Section 6). */
public record DisputeWithdrawnEvent(
        String disputeId,
        String withdrawnAt
) {
}
