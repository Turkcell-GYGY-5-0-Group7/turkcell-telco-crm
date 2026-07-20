package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.domain.Payment;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeResolvedCustomerPaymentConsumerTest {

    @Mock private Mediator mediator;
    @Mock private PaymentRepository paymentRepository;

    private DisputeResolvedCustomerPaymentConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeResolvedCustomerPaymentConsumer(mediator, objectMapper, paymentRepository);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"paymentId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void refunds_completed_payment_via_existing_refund_command() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        payment.markCompleted();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + paymentId + "\"}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        // The consumer dispatches with the loaded Payment's own id, not the raw event paymentId field
        // (both are the "same" payment in a real system - here they intentionally differ to prove
        // the consumer really reads existing.getId(), not passing the JSON field straight through).
        verify(mediator).send(new RefundPaymentCommand(payment.getId(), "DISPUTE_RESOLVED_CUSTOMER", record.key()));
    }

    @Test
    void no_ops_when_payment_not_found() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + paymentId + "\"}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void no_ops_when_payment_not_completed() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-2");
        // still PENDING - not eligible for refund
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + paymentId + "\"}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void no_ops_when_paymentId_is_null_unpaid_invoice_branch() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\"}", "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
