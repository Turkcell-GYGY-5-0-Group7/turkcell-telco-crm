package com.telco.dispute.application.dto;

import com.telco.dispute.domain.DisputeStateHistory;

import java.time.Instant;
import java.util.UUID;

public record DisputeStateHistoryResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        String changedBy,
        Instant changedAt,
        String note
) {
    public static DisputeStateHistoryResponse from(DisputeStateHistory history) {
        return new DisputeStateHistoryResponse(
                history.getId(),
                history.getFromStatus() == null ? null : history.getFromStatus().name(),
                history.getToStatus().name(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getNote());
    }
}
