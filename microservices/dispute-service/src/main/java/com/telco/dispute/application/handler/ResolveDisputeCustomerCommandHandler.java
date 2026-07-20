package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.ResolveDisputeCustomerCommand;
import com.telco.dispute.application.event.DisputeResolvedCustomerEvent;
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
 * Handles {@link ResolveDisputeCustomerCommand}: resolves the dispute in the customer's favor,
 * publishing {@code dispute.resolved-customer.v1} - the ONLY dispute-service event that downstream
 * consumers (billing-service Feature 22.4, payment-service Feature 22.5) may treat as authorization
 * for a real credit/refund (ADR-028 Section 5). dispute-service itself never performs that action.
 */
@Component
public class ResolveDisputeCustomerCommandHandler
        implements CommandHandler<ResolveDisputeCustomerCommand, Unit> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_RESOLVED_CUSTOMER = "dispute.resolved-customer.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public ResolveDisputeCustomerCommandHandler(DisputeRepository disputeRepository,
                                                OutboxService outboxService,
                                                AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(ResolveDisputeCustomerCommand command) {
        Dispute dispute = disputeRepository.findById(command.disputeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Dispute not found: " + command.disputeId(),
                        Map.of("disputeId", command.disputeId())));

        dispute.resolveCustomer(command.resolutionAmount(), command.resolvedBy());

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_RESOLVED_CUSTOMER", AUDIT_ENTITY, dispute.getId().toString(),
                Map.of("resolutionAmount", command.resolutionAmount().toString()));

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_RESOLVED_CUSTOMER,
                new DisputeResolvedCustomerEvent(
                        dispute.getId().toString(),
                        toStringOrNull(dispute.getInvoiceId()),
                        toStringOrNull(dispute.getPaymentId()),
                        dispute.getResolutionAmount(),
                        Instant.now().toString()));

        return Unit.INSTANCE;
    }

    private static String toStringOrNull(UUID id) {
        return Optional.ofNullable(id).map(UUID::toString).orElse(null);
    }
}
