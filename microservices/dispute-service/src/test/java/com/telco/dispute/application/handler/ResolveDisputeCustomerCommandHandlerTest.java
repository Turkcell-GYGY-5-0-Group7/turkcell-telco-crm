package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.ResolveDisputeCustomerCommand;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveDisputeCustomerCommandHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private ResolveDisputeCustomerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResolveDisputeCustomerCommandHandler(disputeRepository, outboxService, auditLogWriter);
    }

    @Test
    void resolves_customer_and_publishes_resolved_customer_event() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", new BigDecimal("30.00"));
        dispute.beginReview("agent-1");
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ResolveDisputeCustomerCommand(disputeId, new BigDecimal("30.00"), "agent-1"));

        verify(outboxService).publish(eq("dispute"), any(), eq("dispute.resolved-customer.v1"), any());
        verify(auditLogWriter).log(eq("DISPUTE_RESOLVED_CUSTOMER"), eq("Dispute"), any(), any());
    }

    @Test
    void throws_not_found_when_dispute_does_not_exist() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new ResolveDisputeCustomerCommand(disputeId, BigDecimal.TEN, "agent-1")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_dispute_not_under_review() {
        UUID disputeId = UUID.randomUUID();
        // still OPENED — resolveCustomer is only legal from UNDER_REVIEW
        Dispute dispute = Dispute.create(UUID.randomUUID(), null, UUID.randomUUID(), "R", BigDecimal.TEN);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> handler.handle(
                new ResolveDisputeCustomerCommand(disputeId, BigDecimal.TEN, "agent-1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
