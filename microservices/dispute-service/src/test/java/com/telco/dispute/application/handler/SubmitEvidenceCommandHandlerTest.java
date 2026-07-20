package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.SubmitEvidenceCommand;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitEvidenceCommandHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private SubmitEvidenceCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SubmitEvidenceCommandHandler(disputeRepository, outboxService, auditLogWriter);
    }

    @Test
    void submits_evidence_and_publishes_evidence_submitted_event() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        dispute.beginReview("agent-1");
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new SubmitEvidenceCommand(disputeId, "customer-1", "dispute-evidence/receipt.pdf",
                customerId.toString(), false));

        verify(outboxService).publish(eq("dispute"), any(), eq("dispute.evidence-submitted.v1"), any());
        verify(auditLogWriter).log(eq("DISPUTE_EVIDENCE_SUBMITTED"), eq("Dispute"), any(), any());
    }

    @Test
    void throws_not_found_when_dispute_does_not_exist() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new SubmitEvidenceCommand(
                disputeId, "customer-1", "ref", UUID.randomUUID().toString(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_dispute_not_under_review() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        // still OPENED — submitEvidence is only legal from UNDER_REVIEW
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new SubmitEvidenceCommand(
                disputeId, "customer-1", "ref", customerId.toString(), false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_access_denied_when_caller_submits_evidence_to_someone_elses_dispute() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        dispute.beginReview("agent-1");
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new SubmitEvidenceCommand(
                disputeId, "customer-1", "ref", UUID.randomUUID().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
