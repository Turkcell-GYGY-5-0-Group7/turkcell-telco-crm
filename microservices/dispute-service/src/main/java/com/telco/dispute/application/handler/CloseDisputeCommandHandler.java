package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.CloseDisputeCommand;
import com.telco.dispute.application.event.DisputeClosedEvent;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Handles {@link CloseDisputeCommand}: closes the dispute, publishing {@code dispute.closed.v1}. */
@Component
public class CloseDisputeCommandHandler implements CommandHandler<CloseDisputeCommand, Unit> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_CLOSED = "dispute.closed.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CloseDisputeCommandHandler(DisputeRepository disputeRepository, OutboxService outboxService,
                                      AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(CloseDisputeCommand command) {
        Dispute dispute = disputeRepository.findById(command.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + command.disputeId(),
                        Map.of("disputeId", command.disputeId())));

        dispute.close(command.closedBy());

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_CLOSED", AUDIT_ENTITY, dispute.getId().toString(), null);

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_CLOSED,
                new DisputeClosedEvent(dispute.getId().toString(), Instant.now().toString()));

        return Unit.INSTANCE;
    }
}
