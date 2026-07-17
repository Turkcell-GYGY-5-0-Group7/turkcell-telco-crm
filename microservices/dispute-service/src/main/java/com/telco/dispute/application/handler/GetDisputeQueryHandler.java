package com.telco.dispute.application.handler;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.dispute.application.query.GetDisputeQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Handles {@link GetDisputeQuery}. {@code @Transactional(readOnly = true)} is load-bearing:
 * {@link DisputeResponse#from} touches {@code Dispute.evidence}/{@code Dispute.history}, both lazy
 * {@code @OneToMany} collections - without this annotation this reproduces the exact
 * {@code LazyInitializationException} bug already documented in {@code docs/tasks/lessons.md}
 * (2026-07-06 entry, order-service).
 */
@Component
public class GetDisputeQueryHandler implements QueryHandler<GetDisputeQuery, DisputeResponse> {

    private final DisputeRepository disputeRepository;

    public GetDisputeQueryHandler(DisputeRepository disputeRepository) {
        this.disputeRepository = disputeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse handle(GetDisputeQuery query) {
        Dispute dispute = disputeRepository.findById(query.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + query.disputeId(),
                        Map.of("disputeId", query.disputeId())));

        if (!query.callerIsAdmin() && !dispute.getCustomerId().toString().equals(query.callerCustomerId())) {
            throw new AccessDeniedException("Dispute does not belong to caller");
        }

        return DisputeResponse.from(dispute);
    }
}
