package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.MarkInvoicePaidCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private PaymentCompletedBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new PaymentCompletedBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void marks_invoice_paid_when_payment_settles_an_invoice() {
        UUID invoiceId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), eq("PaymentCompletedBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"invoiceId\":\"" + invoiceId + "\"}");

        consumer.onPaymentCompleted(record);

        verify(mediator).send(new MarkInvoicePaidCommand(invoiceId));
    }

    @Test
    void ignores_order_driven_payments_with_no_invoiceId() {
        ConsumerRecord<String, String> record = record("{\"paymentId\":\"" + UUID.randomUUID() + "\"}");

        consumer.onPaymentCompleted(record);

        verify(mediator, never()).send(any());
        verify(inboxService, never()).firstSeen(anyString(), anyString());
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("PaymentCompletedBillingConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record(
                "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"invoiceId\":\"" + UUID.randomUUID() + "\"}");

        consumer.onPaymentCompleted(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json");

        assertThatThrownBy(() -> consumer.onPaymentCompleted(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("payment.completed.v1 billing consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("payment.events", 0, 0L, "key-" + UUID.randomUUID(), value);
    }
}
