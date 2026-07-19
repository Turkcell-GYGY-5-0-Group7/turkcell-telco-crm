package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.ResolveDisputeMerchantCommand;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveDisputeMerchantCommandHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private ResolveDisputeMerchantCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResolveDisputeMerchantCommandHandler(disputeRepository, outboxService, auditLogWriter);
    }

    /**
     * Provisional-hold invariant (ADR-028 Section 5): resolving in the merchant's favor never
     * changes {@code resolutionAmount} - no financial change, by contract.
     */
    @Test
    void resolves_merchant_with_no_resolution_amount_and_publishes_resolved_merchant_event() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        dispute.beginReview("agent-1");
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ResolveDisputeMerchantCommand(disputeId, "agent-1"));

        assertThat(dispute.getResolutionAmount()).isNull();
        verify(outboxService).publish(eq("dispute"), any(), eq("dispute.resolved-merchant.v1"), any());
        verify(auditLogWriter).log(eq("DISPUTE_RESOLVED_MERCHANT"), eq("Dispute"), any(), any());
    }

    @Test
    void throws_not_found_when_dispute_does_not_exist() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ResolveDisputeMerchantCommand(disputeId, "agent-1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_dispute_not_under_review() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new ResolveDisputeMerchantCommand(disputeId, "agent-1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
