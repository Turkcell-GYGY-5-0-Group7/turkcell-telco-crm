package com.telco.dispute.application.event;

/** {@code dispute.closed.v1} (ADR-028 Section 6). */
public record DisputeClosedEvent(
        String disputeId,
        String closedAt
) {
}
