package com.telco.dispute.application.dto;

import com.telco.dispute.domain.Dispute;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side representation of a {@link Dispute}, including its evidence and state-history
 * sub-lists.
 *
 * <p><b>Must only ever be constructed inside a transactional boundary</b>
 * ({@code @Transactional(readOnly = true)} on the calling query handler): {@link Dispute#getEvidence()}
 * and {@link Dispute#getHistory()} are lazy {@code @OneToMany} collections, and {@code open-in-view} is
 * disabled platform-wide. Constructing this DTO outside an open session reproduces the exact
 * {@code LazyInitializationException} bug documented in {@code docs/tasks/lessons.md} (2026-07-06 entry,
 * order-service).
 */
public record DisputeResponse(
        UUID id,
        UUID invoiceId,
        UUID paymentId,
        UUID customerId,
        String status,
        String reasonCode,
        BigDecimal disputedAmount,
        BigDecimal resolutionAmount,
        Instant openedAt,
        Instant resolvedAt,
        Instant closedAt,
        List<DisputeEvidenceResponse> evidence,
        List<DisputeStateHistoryResponse> history
) {
    public static DisputeResponse from(Dispute dispute) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getInvoiceId(),
                dispute.getPaymentId(),
                dispute.getCustomerId(),
                dispute.getStatus().name(),
                dispute.getReasonCode(),
                dispute.getDisputedAmount(),
                dispute.getResolutionAmount(),
                dispute.getOpenedAt(),
                dispute.getResolvedAt(),
                dispute.getClosedAt(),
                dispute.getEvidence().stream().map(DisputeEvidenceResponse::from).toList(),
                dispute.getHistory().stream().map(DisputeStateHistoryResponse::from).toList());
    }
}
