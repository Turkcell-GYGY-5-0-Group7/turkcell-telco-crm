package com.telco.usage.application.handler;

import com.telco.usage.application.command.MeterCdrCommand;
import com.telco.usage.application.event.QuotaExceededEvent;
import com.telco.usage.application.event.QuotaThresholdReachedEvent;
import com.telco.usage.application.event.UsageRecordedEvent;
import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageType;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterCdrCommandHandlerTest {

    private static final String AGGREGATE_TYPE = "Quota";
    private static final String EVENT_USAGE_RECORDED = "usage.recorded.v1";
    private static final String EVENT_THRESHOLD_REACHED = "quota.threshold-reached.v1";
    private static final String EVENT_QUOTA_EXCEEDED = "quota.exceeded.v1";

    @Mock
    private QuotaRepository quotaRepository;

    @Mock
    private UsageRecordRepository usageRecordRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private MeterCdrCommandHandler handler;

    private UUID subscriptionId;
    private UUID customerId;
    private Instant periodStart;
    private Instant periodEnd;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        periodStart = Instant.parse("2026-06-01T00:00:00Z");
        periodEnd = Instant.parse("2026-07-01T00:00:00Z");
    }

    @Test
    void duplicate_cdr_ref_skips_all_processing_and_returns_null() {
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 60, Instant.now(), "CDR-DUP");
        when(usageRecordRepository.existsByCdrRef("CDR-DUP")).thenReturn(true);

        Void result = handler.handle(command);

        assertThat(result).isNull();
        verify(quotaRepository, never()).findActiveForUpdateBySubscriptionId(any(), any());
        verify(usageRecordRepository, never()).save(any());
        verify(quotaRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void no_active_quota_defers_processing_and_returns_null() {
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 60, Instant.now(), "CDR-NEW");
        when(usageRecordRepository.existsByCdrRef("CDR-NEW")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.empty());

        Void result = handler.handle(command);

        assertThat(result).isNull();
        verify(usageRecordRepository, never()).save(any());
        verify(quotaRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void happy_path_saves_record_and_quota_and_publishes_only_usage_recorded() {
        // 1000-minute quota, decrement 1 — no threshold (999 > 200), no exceeded
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 1000, 50, 200);
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 1, Instant.now(), "CDR-001");

        when(usageRecordRepository.existsByCdrRef("CDR-001")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        verify(usageRecordRepository).save(any());
        verify(quotaRepository).save(quota);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(1)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), payloadCaptor.capture());

        assertThat(eventTypeCaptor.getValue()).isEqualTo(EVENT_USAGE_RECORDED);
        assertThat(payloadCaptor.getValue()).isInstanceOf(UsageRecordedEvent.class);
    }

    @Test
    void threshold_crossed_publishes_usage_recorded_and_threshold_events() {
        // 100-minute quota, decrement 81 → remaining 19 ≤ 20 → threshold, not exceeded
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 100, 50, 200);
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 81, Instant.now(), "CDR-THR");

        when(usageRecordRepository.existsByCdrRef("CDR-THR")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), any());

        assertThat(eventTypeCaptor.getAllValues())
                .containsExactlyInAnyOrder(EVENT_USAGE_RECORDED, EVENT_THRESHOLD_REACHED);
    }

    @Test
    void exceeded_crossed_publishes_usage_recorded_and_exceeded_events() {
        // Start at threshold-already-notified state: 100 minutes, threshold at 20.
        // Decrement 81 first to trigger threshold → remaining=19, thresholdNotified=true.
        // Then the command decrements 19 → remaining=0, exceeded (threshold already notified).
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 100, 50, 200);
        quota.decrement(UsageType.VOICE, 81); // threshold crossed, remaining=19, thresholdNotified=true

        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 19, Instant.now(), "CDR-EXC");

        when(usageRecordRepository.existsByCdrRef("CDR-EXC")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), any());

        assertThat(eventTypeCaptor.getAllValues())
                .containsExactlyInAnyOrder(EVENT_USAGE_RECORDED, EVENT_QUOTA_EXCEEDED);
    }

    @Test
    void both_threshold_and_exceeded_crossed_publishes_three_events() {
        // total=20, remaining=20, thresholdNotified=false — single decrement of 20 crosses both
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 20, 50, 200);
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 20, Instant.now(), "CDR-BOTH");

        when(usageRecordRepository.existsByCdrRef("CDR-BOTH")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(3)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), any());

        assertThat(eventTypeCaptor.getAllValues())
                .containsExactlyInAnyOrder(EVENT_USAGE_RECORDED, EVENT_THRESHOLD_REACHED, EVENT_QUOTA_EXCEEDED);
    }

    @Test
    void usage_recorded_event_carries_correct_subscription_and_overage_flag() {
        // 100-minute quota; pre-cross the threshold (decrement 81 → remaining=19, thresholdNotified=true).
        // The command then decrements 10 against remaining=19: no overage yet, but that won't do.
        // Instead use remaining=5 after pre-decrement of 95, so decrementing 10 gives overage=true,
        // thresholdCrossed=false (already notified), exceededCrossed=true → exactly 2 events.
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 100, 50, 200);
        quota.decrement(UsageType.VOICE, 95); // remaining=5, 5 <= 100/5=20 → thresholdNotified=true
        // Command: decrement 10 against remaining=5 → overage=true, newRemaining=0,
        // thresholdCrossed=false (already notified), exceededCrossed=true → 2 events
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 10, Instant.now(), "CDR-OVR");

        when(usageRecordRepository.existsByCdrRef("CDR-OVR")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), payloadCaptor.capture());

        int usageRecordedIndex = eventTypeCaptor.getAllValues().indexOf(EVENT_USAGE_RECORDED);
        UsageRecordedEvent event = (UsageRecordedEvent) payloadCaptor.getAllValues().get(usageRecordedIndex);

        assertThat(event.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(event.overage()).isTrue();
        assertThat(event.quantity()).isEqualTo(10);
        assertThat(event.type()).isEqualTo("VOICE");
    }

    @Test
    void threshold_event_carries_correct_subscription_and_quota_ids() {
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 100, 50, 200);
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 81, Instant.now(), "CDR-THR2");

        when(usageRecordRepository.existsByCdrRef("CDR-THR2")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), payloadCaptor.capture());

        int idx = eventTypeCaptor.getAllValues().indexOf(EVENT_THRESHOLD_REACHED);
        QuotaThresholdReachedEvent event = (QuotaThresholdReachedEvent) payloadCaptor.getAllValues().get(idx);

        assertThat(event.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(event.quotaId()).isEqualTo(quota.getId().toString());
        assertThat(event.usageType()).isEqualTo("VOICE");
    }

    @Test
    void exceeded_event_carries_correct_subscription_and_quota_ids() {
        Quota quota = Quota.create(subscriptionId, customerId, periodStart, periodEnd, 20, 50, 200);
        MeterCdrCommand command = new MeterCdrCommand(
                subscriptionId, UsageType.VOICE, 20, Instant.now(), "CDR-EXCV2");

        when(usageRecordRepository.existsByCdrRef("CDR-EXCV2")).thenReturn(false);
        when(quotaRepository.findActiveForUpdateBySubscriptionId(eq(subscriptionId), any()))
                .thenReturn(Optional.of(quota));

        handler.handle(command);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(3)).publish(
                eq(AGGREGATE_TYPE), anyString(), eventTypeCaptor.capture(), payloadCaptor.capture());

        int idx = eventTypeCaptor.getAllValues().indexOf(EVENT_QUOTA_EXCEEDED);
        QuotaExceededEvent event = (QuotaExceededEvent) payloadCaptor.getAllValues().get(idx);

        assertThat(event.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(event.quotaId()).isEqualTo(quota.getId().toString());
        assertThat(event.usageType()).isEqualTo("VOICE");
    }
}
