package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.WithdrawDisputeCommand;
import com.telco.dispute.application.event.DisputeWithdrawnEvent;
import com.telco.dispute.domain.Dispute;
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

/** Handles {@link WithdrawDisputeCommand}: withdraws the dispute, publishing {@code dispute.withdrawn.v1}. */
@Component
public class WithdrawDisputeCommandHandler implements CommandHandler<WithdrawDisputeCommand, Unit> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_WITHDRAWN = "dispute.withdrawn.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public WithdrawDisputeCommandHandler(DisputeRepository disputeRepository, OutboxService outboxService,
                                         AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(WithdrawDisputeCommand command) {
        Dispute dispute = disputeRepository.findById(command.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + command.disputeId(),
                        Map.of("disputeId", command.disputeId())));

        if (!command.callerIsAdmin()
                && !dispute.getCustomerId().toString().equals(command.callerCustomerId())) {
            throw new AccessDeniedException("Dispute does not belong to caller");
        }

        dispute.withdraw(command.withdrawnBy());

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_WITHDRAWN", AUDIT_ENTITY, dispute.getId().toString(), null);

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_WITHDRAWN,
                new DisputeWithdrawnEvent(dispute.getId().toString(), Instant.now().toString()));

        return Unit.INSTANCE;
    }
}
