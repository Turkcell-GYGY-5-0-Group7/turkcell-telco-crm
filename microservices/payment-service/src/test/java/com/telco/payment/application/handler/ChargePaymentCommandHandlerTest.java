package com.telco.payment.application.handler;

import com.telco.payment.application.AuditLogWriter;
import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.event.PaymentCompletedEvent;
import com.telco.payment.application.service.PaymentCreationService;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargePaymentCommandHandlerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCreationService paymentCreationService;
    @Mock private PspAdapter pspAdapter;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private ChargePaymentCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChargePaymentCommandHandler(
                paymentRepository, paymentCreationService, pspAdapter, outboxService, auditLogWriter);
    }

    private ChargePaymentCommand command(String requestId) {
        return new ChargePaymentCommand(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), null, requestId, "msg-" + requestId);
    }

    @Test
    void psp_success_marks_completed_and_publishes_completed_event() throws PspException {
        String reqId = "REQ-001";
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.empty());
        Payment newPayment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        when(paymentCreationService.saveNewPayment(any())).thenReturn(newPayment);
        when(pspAdapter.charge(anyString(), any(), anyString()))
                .thenReturn(new ChargeResult("TXN-001"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = handler.handle(command(reqId));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.completed.v1"), any());
        verify(auditLogWriter).log(eq("PAYMENT_COMPLETED"), eq("Payment"), anyString(), any());
    }

    @Test
    void psp_failure_marks_failed_and_publishes_failed_event() throws PspException {
        String reqId = "REQ-002";
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.empty());
        Payment newPayment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        when(paymentCreationService.saveNewPayment(any())).thenReturn(newPayment);
        when(pspAdapter.charge(anyString(), any(), anyString()))
                .thenThrow(new PspException("Card declined"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = handler.handle(command(reqId));

        assertThat(response.status()).isEqualTo("FAILED");
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.failed.v1"), any());
        verify(auditLogWriter).log(eq("PAYMENT_FAILED"), eq("Payment"), anyString(), any());
    }

    @Test
    void idempotent_return_for_existing_completed_payment_without_re_charging() throws PspException {
        String reqId = "REQ-003";
        Payment existing = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        existing.markCompleted();
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.of(existing));

        PaymentResponse response = handler.handle(command(reqId));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(pspAdapter, never()).charge(any(), any(), any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
        verify(auditLogWriter, never()).log(any(), any(), any(), any());
    }

    @Test
    void idempotent_return_for_existing_refunded_payment_without_re_charging() throws PspException {
        String reqId = "REQ-004";
        Payment existing = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        existing.markCompleted();
        existing.markRefunded();
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.of(existing));

        PaymentResponse response = handler.handle(command(reqId));

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(pspAdapter, never()).charge(any(), any(), any());
    }

    @Test
    void psp_success_with_invoice_id_carries_it_onto_completed_event() throws PspException {
        String reqId = "REQ-INV-001";
        UUID invoiceId = UUID.randomUUID();
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.empty());
        Payment newPayment = Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), reqId, invoiceId);
        when(paymentCreationService.saveNewPayment(any())).thenReturn(newPayment);
        when(pspAdapter.charge(anyString(), any(), anyString()))
                .thenReturn(new ChargeResult("TXN-INV-001"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChargePaymentCommand command = new ChargePaymentCommand(newPayment.getOrderId(),
                newPayment.getCustomerId(), new BigDecimal("49.99"), invoiceId, reqId, "msg-" + reqId);

        PaymentResponse response = handler.handle(command);

        assertThat(response.status()).isEqualTo("COMPLETED");
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.completed.v1"),
                payloadCaptor.capture());
        PaymentCompletedEvent event = (PaymentCompletedEvent) payloadCaptor.getValue();
        assertThat(event.invoiceId()).isEqualTo(invoiceId.toString());
    }

    @Test
    void retries_psp_for_existing_failed_payment() throws PspException {
        String reqId = "REQ-005";
        Payment failed = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        failed.markFailed();
        when(paymentRepository.findByPaymentRequestId(reqId)).thenReturn(Optional.of(failed));
        when(pspAdapter.charge(anyString(), any(), anyString()))
                .thenReturn(new ChargeResult("TXN-RETRY"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = handler.handle(command(reqId));

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(pspAdapter).charge(anyString(), any(), anyString());
    }
}
