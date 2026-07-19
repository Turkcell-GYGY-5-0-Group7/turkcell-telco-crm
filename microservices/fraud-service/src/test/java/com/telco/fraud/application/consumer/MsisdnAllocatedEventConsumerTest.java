package com.telco.fraud.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.fraud.application.command.IngestLifecycleSignalCommand;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
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
class MsisdnAllocatedEventConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private MsisdnAllocatedEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new MsisdnAllocatedEventConsumer(mediator, inboxService, objectMapper);
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "subscription.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    void ingests_allocation_and_dispatches_command() {
        String eventId = UUID.randomUUID().toString();
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(eq(eventId), eq("MsisdnAllocatedEventConsumer"))).thenReturn(true);

        String json = "{\"eventId\":\"" + eventId + "\",\"msisdn\":\"905550001122\","
                + "\"subscriptionId\":\"" + subscriptionId + "\",\"customerId\":\"" + customerId
                + "\",\"allocatedAt\":" + System.currentTimeMillis() + "}";

        consumer.onMsisdnAllocated(record(json, "msisdn.allocated.v1"));

        ArgumentCaptor<IngestLifecycleSignalCommand> captor =
                ArgumentCaptor.forClass(IngestLifecycleSignalCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(MsisdnLifecycleEventType.MSISDN_ALLOCATED);
        assertThat(captor.getValue().subscriptionId()).isEqualTo(subscriptionId);
        assertThat(captor.getValue().customerId()).isEqualTo(customerId);
    }

    @Test
    void ignores_event_of_a_different_type_on_the_shared_topic() {
        consumer.onMsisdnAllocated(record(
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\"}", "msisdn.released.v1"));

        verify(inboxService, never()).firstSeen(anyString(), anyString());
        verify(mediator, never()).send(any());
    }

    @Test
    void skips_duplicate_already_seen_in_inbox() {
        String eventId = UUID.randomUUID().toString();
        when(inboxService.firstSeen(eq(eventId), eq("MsisdnAllocatedEventConsumer"))).thenReturn(false);

        String json = "{\"eventId\":\"" + eventId + "\",\"msisdn\":\"905550001122\","
                + "\"subscriptionId\":\"" + UUID.randomUUID() + "\"}";

        consumer.onMsisdnAllocated(record(json, "msisdn.allocated.v1"));

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        assertThatThrownBy(() -> consumer.onMsisdnAllocated(record("not-json", "msisdn.allocated.v1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("msisdn.allocated.v1 processing failed");
    }
}
