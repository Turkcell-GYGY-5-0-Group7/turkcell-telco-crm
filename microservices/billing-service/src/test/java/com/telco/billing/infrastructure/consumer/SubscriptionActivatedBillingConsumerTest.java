package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionActivatedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the branches {@code BillingAC02IntegrationTest} never exercises directly: the
 * activation-filter skip, the inbox dedup skip, and the failure-wrapping/retry path (14.3.2 saga-
 * path coverage pass — see docs/tasks/sprint-14-testing-and-hardening/README.md).
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionActivatedBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private SubscriptionActivatedBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionActivatedBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void processes_activation_event_and_sends_command() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        long activatedAtMillis = Instant.now().toEpochMilli();
        when(inboxService.firstSeen(anyString(), eq("SubscriptionActivatedBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record("subscription.events",
                "{\"subscriptionId\":\"" + subscriptionId + "\",\"customerId\":\"" + customerId
                        + "\",\"tariffCode\":\"POSTPAID-M\",\"activatedAt\":" + activatedAtMillis + "}");

        consumer.onSubscriptionActivated(record);

        verify(mediator).send(new RecordSubscriptionActivatedCommand(
                subscriptionId, customerId, "POSTPAID-M", Instant.ofEpochMilli(activatedAtMillis)));
    }

    @Test
    void ignores_non_activation_subscription_event_missing_tariff_code() {
        ConsumerRecord<String, String> record = record("subscription.events",
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\",\"customerId\":\""
                        + UUID.randomUUID() + "\"}");

        consumer.onSubscriptionActivated(record);

        verify(mediator, never()).send(any());
        verify(inboxService, never()).firstSeen(anyString(), anyString());
    }

    @Test
    void skips_when_required_fields_missing() {
        ConsumerRecord<String, String> record = record("subscription.events", "{}");

        consumer.onSubscriptionActivated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("SubscriptionActivatedBillingConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record("subscription.events",
                "{\"subscriptionId\":\"" + UUID.randomUUID() + "\",\"customerId\":\""
                        + UUID.randomUUID() + "\",\"tariffCode\":\"POSTPAID-S\"}");

        consumer.onSubscriptionActivated(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void defaults_activatedAt_to_now_when_absent() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), anyString())).thenReturn(true);
        ConsumerRecord<String, String> record = record("subscription.events",
                "{\"subscriptionId\":\"" + subscriptionId + "\",\"customerId\":\"" + customerId
                        + "\",\"tariffCode\":\"POSTPAID-S\"}");

        consumer.onSubscriptionActivated(record);

        verify(mediator, times(1)).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("subscription.events", "not-json");

        assertThatThrownBy(() -> consumer.onSubscriptionActivated(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("subscription.activated.v1 billing consumer failed");
    }

    private static ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key-" + UUID.randomUUID(), value);
    }
}
