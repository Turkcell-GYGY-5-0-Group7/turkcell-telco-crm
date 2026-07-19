package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.MarkPaymentDisputedCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisputeOpenedPaymentConsumerTest {

    @Mock private Mediator mediator;

    private DisputeOpenedPaymentConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeOpenedPaymentConsumer(mediator, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"paymentId\":\"" + UUID.randomUUID() + "\"}", "dispute.resolved-customer.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void marks_referenced_payment_disputed() {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + paymentId + "\"}",
                "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator).send(new MarkPaymentDisputedCommand(paymentId, record.key()));
    }

    @Test
    void no_ops_when_paymentId_is_null_invoice_only_dispute() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json", "dispute.opened.v1");

        assertThatThrownBy(() -> consumer.onDisputeOpened(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("dispute.opened.v1 payment consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
