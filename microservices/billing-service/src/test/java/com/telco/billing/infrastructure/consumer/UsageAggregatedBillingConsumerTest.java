package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordOverageCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
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
class UsageAggregatedBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private UsageAggregatedBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new UsageAggregatedBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void records_overage_for_a_well_formed_usage_aggregate_defaulting_aggregatedAt_to_now() {
        UUID subscriptionId = UUID.randomUUID();
        Instant periodStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-08-01T00:00:00Z");
        when(inboxService.firstSeen(anyString(), eq("UsageAggregatedBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(String.format("""
                {"subscriptionId":"%s","periodStart":"%s","periodEnd":"%s",
                 "voiceOverageSeconds":120,"smsOverageCount":5,"dataOverageKb":2048}
                """, subscriptionId, periodStart, periodEnd));

        consumer.onUsageAggregated(record);

        ArgumentCaptor<RecordOverageCommand> captor = ArgumentCaptor.forClass(RecordOverageCommand.class);
        verify(mediator).send(captor.capture());
        RecordOverageCommand command = captor.getValue();
        assertThat(command.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(command.periodStart()).isEqualTo(periodStart);
        assertThat(command.periodEnd()).isEqualTo(periodEnd);
        assertThat(command.voiceOverageSeconds()).isEqualTo(120);
        assertThat(command.smsOverageCount()).isEqualTo(5);
        assertThat(command.dataOverageKb()).isEqualTo(2048);
        // aggregatedAt was absent from the payload, so the consumer defaults it to "now".
        assertThat(Duration.between(command.aggregatedAt(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void skips_when_subscriptionId_or_periodStart_missing() {
        ConsumerRecord<String, String> record = record("{\"periodEnd\":\"2026-08-01T00:00:00Z\"}");

        consumer.onUsageAggregated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("UsageAggregatedBillingConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record(String.format("""
                {"subscriptionId":"%s","periodStart":"2026-07-01T00:00:00Z","periodEnd":"2026-08-01T00:00:00Z",
                 "voiceOverageSeconds":0,"smsOverageCount":0,"dataOverageKb":0}
                """, UUID.randomUUID()));

        consumer.onUsageAggregated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json");

        assertThatThrownBy(() -> consumer.onUsageAggregated(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("usage.aggregated.v1 billing consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("usage.events", 0, 0L, "key-" + UUID.randomUUID(), value);
    }
}
