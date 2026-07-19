package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.LogStaleTariffReferenceCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TariffCreatedEventConsumerTest {

    @Mock private Mediator mediator;

    private TariffCreatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TariffCreatedEventConsumer(mediator, new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String messageId, String code, String eventType) {
        String json = "{\"tariffId\":\"t-1\",\"code\":\"" + code + "\",\"name\":\"n\",\"type\":\"POSTPAID\","
                + "\"monthlyFee\":49.99,\"currency\":\"TRY\",\"effectiveFrom\":\"2026-01-01\","
                + "\"createdAt\":\"2026-07-13T00:00:00Z\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("tariff.events", 0, 0L, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void dispatches_log_command_for_tariff_created() {
        consumer.onTariffCreated(record("msg-1", "TARIFF-A", "tariff.created.v1"));

        verify(mediator).send(eq(new LogStaleTariffReferenceCommand("TARIFF-A", "msg-1")));
    }

    @Test
    void ignores_non_tariff_created_event_types() {
        consumer.onTariffCreated(record("msg-2", "TARIFF-A", "tariff.price-changed.v1"));

        verify(mediator, never()).send(any());
    }

    @Test
    void redelivering_the_same_message_id_dispatches_an_identical_idempotency_key_each_time() {
        // Duplicate delivery of the same messageId must yield an equal command each time so the
        // platform InboxBehavior dedups it to a single logged WARN, however many times this consumer
        // itself is invoked for the redelivered record.
        consumer.onTariffCreated(record("msg-dup-1", "TARIFF-A", "tariff.created.v1"));
        consumer.onTariffCreated(record("msg-dup-1", "TARIFF-A", "tariff.created.v1"));

        LogStaleTariffReferenceCommand expected = new LogStaleTariffReferenceCommand("TARIFF-A", "msg-dup-1");
        verify(mediator, org.mockito.Mockito.times(2)).send(eq(expected));
    }
}
