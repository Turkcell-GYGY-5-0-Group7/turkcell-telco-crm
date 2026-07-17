package com.telco.dispute.application.handler;

import com.telco.dispute.application.query.GetDisputeEvidenceDownloadUrlQuery;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.domain.DisputeEvidence;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.dispute.infrastructure.storage.DisputeEvidenceStorage;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

/** Handles {@link GetDisputeEvidenceDownloadUrlQuery}. */
@Component
public class GetDisputeEvidenceDownloadUrlQueryHandler
        implements QueryHandler<GetDisputeEvidenceDownloadUrlQuery, String> {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceStorage disputeEvidenceStorage;

    public GetDisputeEvidenceDownloadUrlQueryHandler(DisputeRepository disputeRepository,
                                                     DisputeEvidenceStorage disputeEvidenceStorage) {
        this.disputeRepository = disputeRepository;
        this.disputeEvidenceStorage = disputeEvidenceStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public String handle(GetDisputeEvidenceDownloadUrlQuery query) {
        Dispute dispute = disputeRepository.findById(query.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + query.disputeId(),
                        Map.of("disputeId", query.disputeId())));

        if (!query.callerIsAdmin() && !dispute.getCustomerId().toString().equals(query.callerCustomerId())) {
            throw new AccessDeniedException("Dispute does not belong to caller");
        }

        DisputeEvidence evidence = dispute.getEvidence().stream()
                .filter(e -> e.getId().equals(query.evidenceId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Evidence not found: " + query.evidenceId(),
                        Map.of("evidenceId", query.evidenceId())));

        return disputeEvidenceStorage.presignedGetUrl(evidence.getObjectRef(), DOWNLOAD_URL_TTL);
    }
}
