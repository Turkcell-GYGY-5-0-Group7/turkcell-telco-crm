package com.telco.dispute.application.dto;

import com.telco.dispute.domain.DisputeEvidence;

import java.time.Instant;
import java.util.UUID;

public record DisputeEvidenceResponse(
        UUID id,
        String submittedBy,
        String objectRef,
        Instant submittedAt
) {
    public static DisputeEvidenceResponse from(DisputeEvidence evidence) {
        return new DisputeEvidenceResponse(
                evidence.getId(), evidence.getSubmittedBy(), evidence.getObjectRef(), evidence.getSubmittedAt());
    }
}
