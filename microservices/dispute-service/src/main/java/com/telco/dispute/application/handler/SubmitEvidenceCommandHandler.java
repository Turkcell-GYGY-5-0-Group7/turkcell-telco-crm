package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.SubmitEvidenceCommand;
import com.telco.dispute.application.event.DisputeEvidenceSubmittedEvent;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.domain.DisputeEvidence;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Handles {@link SubmitEvidenceCommand}: attaches an evidence object reference and transitions the
 * dispute to {@code EVIDENCE_SUBMITTED}, publishing {@code dispute.evidence-submitted.v1}.
 */
@Component
public class SubmitEvidenceCommandHandler implements CommandHandler<SubmitEvidenceCommand, Unit> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_EVIDENCE_SUBMITTED = "dispute.evidence-submitted.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public SubmitEvidenceCommandHandler(DisputeRepository disputeRepository, OutboxService outboxService,
                                        AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(SubmitEvidenceCommand command) {
        Dispute dispute = disputeRepository.findById(command.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + command.disputeId(),
                        Map.of("disputeId", command.disputeId())));

        if (!command.callerIsAdmin()
                && !dispute.getCustomerId().toString().equals(command.callerCustomerId())) {
            throw new AccessDeniedException("Dispute does not belong to caller");
        }

        DisputeEvidence evidence = dispute.addEvidence(command.submittedBy(), command.objectRef());
        dispute.submitEvidence(command.submittedBy(), null);

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_EVIDENCE_SUBMITTED", AUDIT_ENTITY, dispute.getId().toString(),
                Map.of("evidenceId", evidence.getId().toString(), "submittedBy", command.submittedBy()));

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_EVIDENCE_SUBMITTED,
                new DisputeEvidenceSubmittedEvent(
                        dispute.getId().toString(),
                        evidence.getId().toString(),
                        command.submittedBy(),
                        Instant.now().toString()));

        return Unit.INSTANCE;
    }
}
