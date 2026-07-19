package com.telco.dispute.application.query;

import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Returns a time-limited presigned URL for a piece of dispute evidence. A non-admin caller may only
 * download evidence from their own dispute -
 * {@link com.telco.dispute.application.handler.GetDisputeEvidenceDownloadUrlQueryHandler} enforces this.
 */
public record GetDisputeEvidenceDownloadUrlQuery(
        UUID disputeId,
        UUID evidenceId,
        String callerCustomerId,
        boolean callerIsAdmin
) implements Query<String> {
}
