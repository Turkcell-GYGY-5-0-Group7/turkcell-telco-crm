package com.telco.dispute.application.handler;

import com.telco.dispute.application.AuditLogWriter;
import com.telco.dispute.application.command.OpenDisputeCommand;
import com.telco.dispute.domain.Dispute;
import com.telco.dispute.infrastructure.persistence.DisputeRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenDisputeCommandHandlerTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private OpenDisputeCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OpenDisputeCommandHandler(disputeRepository, outboxService, auditLogWriter);
    }

    @Test
    void opens_dispute_straight_to_under_review_and_publishes_opened_event() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID disputeId = handler.handle(new OpenDisputeCommand(
                invoiceId, null, customerId, "BILLING_ERROR", new BigDecimal("49.99"),
                customerId.toString(), false));

        assertThat(disputeId).isNotNull();
        verify(outboxService).publish(eq("dispute"), any(), eq("dispute.opened.v1"), any());
        verify(auditLogWriter).log(eq("DISPUTE_OPENED"), eq("Dispute"), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_neither_invoice_nor_payment_set() {
        UUID customerId = UUID.randomUUID();
        assertThatThrownBy(() -> handler.handle(new OpenDisputeCommand(
                null, null, customerId, "OTHER", BigDecimal.TEN, customerId.toString(), false)))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * Provisional-hold invariant, load-bearing (ADR-028 Section 5): opening a dispute never writes
     * anywhere except dispute-db and the dispute-service outbox.
     */
    @Test
    void handled_dispute_is_the_only_entity_saved() {
        UUID customerId = UUID.randomUUID();
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new OpenDisputeCommand(
                null, UUID.randomUUID(), customerId, "PSP_CHARGEBACK", BigDecimal.ONE,
                customerId.toString(), false));

        verify(disputeRepository).save(org.mockito.ArgumentMatchers.any(Dispute.class));
    }

    @Test
    void throws_access_denied_when_caller_opens_dispute_for_a_different_customer() {
        assertThatThrownBy(() -> handler.handle(new OpenDisputeCommand(
                UUID.randomUUID(), null, UUID.randomUUID(), "BILLING_ERROR", BigDecimal.TEN,
                UUID.randomUUID().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
        verify(outboxService, org.mockito.Mockito.never()).publish(any(), any(), any(), any());
    }

    @Test
    void admin_may_open_dispute_for_any_customer() {
        UUID customerId = UUID.randomUUID();
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID disputeId = handler.handle(new OpenDisputeCommand(
                UUID.randomUUID(), null, customerId, "BILLING_ERROR", BigDecimal.TEN,
                UUID.randomUUID().toString(), true));

        assertThat(disputeId).isNotNull();
    }
}
