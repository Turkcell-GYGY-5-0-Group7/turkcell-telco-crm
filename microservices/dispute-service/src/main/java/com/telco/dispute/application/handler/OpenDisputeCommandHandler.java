package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.OpenDisputeCommand;
import com.telco.dispute.application.event.DisputeOpenedEvent;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.context.UserContextHolder;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles {@link OpenDisputeCommand}: creates a new {@link Dispute} and moves it straight to
 * {@code UNDER_REVIEW}, publishing {@code dispute.opened.v1}.
 *
 * <p><b>Provisional-hold invariant (ADR-028 Section 5):</b> this handler writes only to
 * {@code dispute-db} and the dispute-service outbox - no HTTP call, no Kafka call outside the
 * outbox, and no write of any kind to {@code billing-db}/{@code payment-db}. {@code dispute.opened.v1}
 * is the only signal a hold ever travels on.
 */
@Component
public class OpenDisputeCommandHandler implements CommandHandler<OpenDisputeCommand, UUID> {

    private static final String OUTBOX_AGGREGATE_TYPE = "dispute";
    private static final String AUDIT_ENTITY = "Dispute";
    private static final String EVENT_OPENED = "dispute.opened.v1";

    private final DisputeRepository disputeRepository;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public OpenDisputeCommandHandler(DisputeRepository disputeRepository, OutboxService outboxService,
                                     AuditLogWriter auditLogWriter) {
        this.disputeRepository = disputeRepository;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UUID handle(OpenDisputeCommand command) {
        if (!command.callerIsAdmin()
                && !command.customerId().toString().equals(command.callerCustomerId())) {
            throw new AccessDeniedException("Cannot open a dispute for a different customer");
        }

        String actor = UserContextHolder.get().map(u -> u.userId()).orElse("system");

        Dispute dispute = Dispute.create(command.invoiceId(), command.paymentId(), command.customerId(),
                command.reasonCode(), command.disputedAmount());
        dispute.beginReview(actor);

        disputeRepository.save(dispute);

        auditLogWriter.log("DISPUTE_OPENED", AUDIT_ENTITY, dispute.getId().toString(),
                Map.of("reasonCode", command.reasonCode(), "disputedAmount", command.disputedAmount().toString()));

        outboxService.publish(OUTBOX_AGGREGATE_TYPE, dispute.getId().toString(), EVENT_OPENED,
                new DisputeOpenedEvent(
                        dispute.getId().toString(),
                        toStringOrNull(dispute.getInvoiceId()),
                        toStringOrNull(dispute.getPaymentId()),
                        dispute.getCustomerId().toString(),
                        dispute.getDisputedAmount(),
                        dispute.getReasonCode(),
                        Instant.now().toString()));

        return dispute.getId();
    }

    private static String toStringOrNull(UUID id) {
        return Optional.ofNullable(id).map(UUID::toString).orElse(null);
    }
}
