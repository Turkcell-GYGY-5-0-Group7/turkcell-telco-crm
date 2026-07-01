package com.telco.usage.application.handler;

import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.usage.application.event.UsageAggregatedEvent;
import com.telco.usage.domain.UsageType;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregateUsageCommandHandlerTest {

    // usage.aggregated.v1 routes to the lowercase `usage` domain (-> usage.events), which
    // billing-service subscribes to. A PascalCase value routes to the wrong topic (ADR-009).
    private static final String AGGREGATE_TYPE = "usage";
    private static final String EVENT_TYPE = "usage.aggregated.v1";

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private AggregateUsageCommandHandler handler;

    private UUID subscriptionId;
    private Instant periodStart;
    private Instant periodEnd;
    private AggregateUsageCommand command;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        periodStart = Instant.parse("2026-06-01T00:00:00Z");
        periodEnd = Instant.parse("2026-07-01T00:00:00Z");
        command = new AggregateUsageCommand(subscriptionId, periodStart, periodEnd);
    }

    @Test
    void all_types_have_overage_returns_correct_sums_and_publishes_event() {
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.VOICE), any(), any())).thenReturn(120L);
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.SMS), any(), any())).thenReturn(5L);
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.DATA), any(), any())).thenReturn(1024L);

        UsageAggregateResponse response = handler.handle(command);

        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(response.voiceOverageSeconds()).isEqualTo(120L);
        assertThat(response.smsOverageCount()).isEqualTo(5L);
        assertThat(response.dataOverageKb()).isEqualTo(1024L);
        assertThat(response.periodStart()).isEqualTo(periodStart);
        assertThat(response.periodEnd()).isEqualTo(periodEnd);

        verify(outboxService, times(1)).publish(
                eq(AGGREGATE_TYPE), eq(subscriptionId.toString()), eq(EVENT_TYPE), any());
    }

    @Test
    void null_sums_default_to_zero_in_response_and_event() {
        when(usageRecordRepository.sumOverageBySubscriptionAndType(any(), any(), any(), any()))
                .thenReturn(null);

        UsageAggregateResponse response = handler.handle(command);

        assertThat(response.voiceOverageSeconds()).isZero();
        assertThat(response.smsOverageCount()).isZero();
        assertThat(response.dataOverageKb()).isZero();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(any(), any(), any(), payloadCaptor.capture());
        UsageAggregatedEvent event = (UsageAggregatedEvent) payloadCaptor.getValue();
        assertThat(event.voiceOverageSeconds()).isZero();
        assertThat(event.smsOverageCount()).isZero();
        assertThat(event.dataOverageKb()).isZero();
    }

    @Test
    void mixed_null_and_non_null_sums_handled_correctly() {
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.VOICE), any(), any())).thenReturn(60L);
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.SMS), any(), any())).thenReturn(null);
        when(usageRecordRepository.sumOverageBySubscriptionAndType(
                eq(subscriptionId), eq(UsageType.DATA), any(), any())).thenReturn(512L);

        UsageAggregateResponse response = handler.handle(command);

        assertThat(response.voiceOverageSeconds()).isEqualTo(60L);
        assertThat(response.smsOverageCount()).isZero();
        assertThat(response.dataOverageKb()).isEqualTo(512L);
    }

    @Test
    void published_event_carries_subscription_and_period_information() {
        when(usageRecordRepository.sumOverageBySubscriptionAndType(any(), any(), any(), any()))
                .thenReturn(10L);

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(any(), any(), any(), payloadCaptor.capture());
        UsageAggregatedEvent event = (UsageAggregatedEvent) payloadCaptor.getValue();

        assertThat(event.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(event.periodStart()).isEqualTo(periodStart.toString());
        assertThat(event.periodEnd()).isEqualTo(periodEnd.toString());
        assertThat(event.aggregatedAt()).isNotBlank();
    }
}
