package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.ResolveDisputeMerchantCommand;
import com.telco.dispute.application.event.DisputeResolvedMerchantEvent;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Handles {@link ResolveDisputeMerchantCommand}: resolves the dispute in the merchant's favor -
 * simply clears the hold, publishing {@code dispute.resolved-merchant.v1} with NO financial change
 * by contract (ADR-028 Section 5).
 */
@Component
public class ResolveDisputeMerchantCommandHandler
        implements CommandHandler<ResolveDisputeMerchantCommand, Unit> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_RESOLVED_MERCHANT = "dispute.resolved-merchant.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public ResolveDisputeMerchantCommandHandler(DisputeRepository disputeRepository,
                                                OutboxService outboxService,
                                                AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(ResolveDisputeMerchantCommand command) {
        Dispute dispute = disputeRepository.findById(command.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + command.disputeId(),
                        Map.of("disputeId", command.disputeId())));

        dispute.resolveMerchant(command.resolvedBy());

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_RESOLVED_MERCHANT", AUDIT_ENTITY, dispute.getId().toString(), null);

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_RESOLVED_MERCHANT,
                new DisputeResolvedMerchantEvent(
                        dispute.getId().toString(),
                        toStringOrNull(dispute.getInvoiceId()),
                        toStringOrNull(dispute.getPaymentId()),
                        Instant.now().toString()));

        return Unit.INSTANCE;
    }

    private static String toStringOrNull(UUID id) {
        return Optional.ofNullable(id).map(UUID::toString).orElse(null);
    }
}
