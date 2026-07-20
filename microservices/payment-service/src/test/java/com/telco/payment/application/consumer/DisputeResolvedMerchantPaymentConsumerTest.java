package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.ClearPaymentDisputedCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisputeResolvedMerchantPaymentConsumerTest {

    @Mock private Mediator mediator;

    private DisputeResolvedMerchantPaymentConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeResolvedMerchantPaymentConsumer(mediator, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"paymentId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeResolvedMerchant(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void clears_disputed_flag_on_referenced_payment() {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + paymentId + "\"}",
                "dispute.resolved-merchant.v1");

        consumer.onDisputeResolvedMerchant(record);

        verify(mediator).send(new ClearPaymentDisputedCommand(paymentId, record.key()));
    }

    @Test
    void no_ops_when_paymentId_is_null() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\"}", "dispute.resolved-merchant.v1");

        consumer.onDisputeResolvedMerchant(record);

        verify(mediator, never()).send(any());
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
