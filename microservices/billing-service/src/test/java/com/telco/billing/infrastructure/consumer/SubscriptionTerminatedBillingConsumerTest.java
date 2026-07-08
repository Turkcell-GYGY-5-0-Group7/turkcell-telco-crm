package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionTerminatedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTerminatedBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private SubscriptionTerminatedBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionTerminatedBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\"}", "subscription.activated.v1");

        consumer.onSubscriptionTerminated(record);

        verify(mediator, never()).send(any());
        verify(inboxService, never()).firstSeen(anyString(), anyString());
    }

    @Test
    void processes_termination_event_using_epoch_millis_timestamp() {
        UUID subscriptionId = UUID.randomUUID();
        long terminatedAtMillis = Instant.now().toEpochMilli();
        when(inboxService.firstSeen(anyString(), eq("SubscriptionTerminatedBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"subscriptionId\":\"" + subscriptionId + "\",\"terminatedAt\":" + terminatedAtMillis + "}",
                "subscription.terminated.v1");

        consumer.onSubscriptionTerminated(record);

        verify(mediator).send(new RecordSubscriptionTerminatedCommand(
                subscriptionId, Instant.ofEpochMilli(terminatedAtMillis)));
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("SubscriptionTerminatedBillingConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record(
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\"}", "subscription.terminated.v1");

        consumer.onSubscriptionTerminated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void skips_when_subscriptionId_missing() {
        ConsumerRecord<String, String> record = record("{}", "subscription.terminated.v1");

        consumer.onSubscriptionTerminated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json", "subscription.terminated.v1");

        assertThatThrownBy(() -> consumer.onSubscriptionTerminated(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("subscription.terminated.v1 billing consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "subscription.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
