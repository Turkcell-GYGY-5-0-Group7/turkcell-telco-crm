package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.WithdrawDisputeCommand;
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
class WithdrawDisputeCommandHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private WithdrawDisputeCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WithdrawDisputeCommandHandler(disputeRepository, outboxService, auditLogWriter);
    }

    @Test
    void withdraws_dispute_and_publishes_withdrawn_event() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new WithdrawDisputeCommand(disputeId, "customer-1", customerId.toString(), false));

        verify(outboxService).publish(eq("dispute"), any(), eq("dispute.withdrawn.v1"), any());
        verify(auditLogWriter).log(eq("DISPUTE_WITHDRAWN"), eq("Dispute"), any(), any());
    }

    @Test
    void throws_not_found_when_dispute_does_not_exist() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new WithdrawDisputeCommand(
                disputeId, "customer-1", UUID.randomUUID().toString(), false)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_dispute_already_closed() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, customerId, "R", BigDecimal.TEN);
        dispute.beginReview("agent-1");
        dispute.resolveMerchant("agent-1");
        dispute.close("agent-1");
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new WithdrawDisputeCommand(
                disputeId, "customer-1", customerId.toString(), false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_access_denied_when_caller_withdraws_someone_elses_dispute() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(new WithdrawDisputeCommand(
                disputeId, "customer-1", UUID.randomUUID().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
