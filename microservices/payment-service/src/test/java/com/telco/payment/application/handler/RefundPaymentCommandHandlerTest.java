package com.telco.payment.application.handler;

import com.telco.payment.application.AuditLogWriter;
import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.payment.infrastructure.psp.PspAdapter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundPaymentCommandHandlerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PspAdapter pspAdapter;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private RefundPaymentCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefundPaymentCommandHandler(paymentRepository, pspAdapter, outboxService, auditLogWriter);
    }

    @Test
    void refunds_completed_payment_and_publishes_refunded_event() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        payment.markCompleted();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pspAdapter.refund(anyString(), any(), anyString())).thenReturn(null);

        PaymentResponse response = handler.handle(new RefundPaymentCommand(paymentId, "Customer request", "msg-refund-1"));

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.refunded.v1"), any());
        verify(auditLogWriter).log(eq("PAYMENT_REFUNDED"), eq("Payment"), anyString(), any());
    }

    @Test
    void throws_not_found_when_payment_does_not_exist() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RefundPaymentCommand(paymentId, "reason", "msg-refund-2")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_refunding_non_completed_payment() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-2");
        // payment status is PENDING — only COMPLETED can be refunded
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> handler.handle(new RefundPaymentCommand(paymentId, "reason", "msg-refund-2")))
                .isInstanceOf(BusinessRuleException.class);
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
