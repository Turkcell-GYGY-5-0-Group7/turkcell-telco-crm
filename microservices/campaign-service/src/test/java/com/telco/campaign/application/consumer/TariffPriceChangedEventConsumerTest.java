package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.FlagStaleTariffReferenceCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TariffPriceChangedEventConsumerTest {

    @Mock private Mediator mediator;

    private TariffPriceChangedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TariffPriceChangedEventConsumer(mediator, new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String messageId, String code, String eventType) {
        String json = "{\"tariffId\":\"t-1\",\"code\":\"" + code + "\",\"oldMonthlyFee\":49.99,"
                + "\"newMonthlyFee\":59.99,\"currency\":\"TRY\",\"newVersion\":2,"
                + "\"changedAt\":\"2026-07-13T00:00:00Z\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("tariff.events", 0, 0L, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void dispatches_flag_command_for_tariff_price_changed() {
        consumer.onTariffPriceChanged(record("msg-1", "TARIFF-A", "tariff.price-changed.v1"));

        ArgumentCaptor<FlagStaleTariffReferenceCommand> captor =
                ArgumentCaptor.forClass(FlagStaleTariffReferenceCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().tariffCode()).isEqualTo("TARIFF-A");
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("msg-1");
        assertThat(captor.getValue().reason()).contains("tariff.price-changed.v1");
    }

    @Test
    void ignores_non_price_changed_event_types() {
        consumer.onTariffPriceChanged(record("msg-2", "TARIFF-A", "tariff.created.v1"));

        verify(mediator, never()).send(any());
    }

    @Test
    void redelivering_the_same_message_id_dispatches_an_identical_idempotency_key_each_time() {
        // Duplicate delivery of the same messageId must yield an equal command (including the same
        // reason text, derived deterministically from the payload) each time, so the platform
        // InboxBehavior dedups it to a single flag/refresh, however many times this consumer itself is
        // invoked for the redelivered record.
        consumer.onTariffPriceChanged(record("msg-dup-1", "TARIFF-A", "tariff.price-changed.v1"));
        consumer.onTariffPriceChanged(record("msg-dup-1", "TARIFF-A", "tariff.price-changed.v1"));

        ArgumentCaptor<FlagStaleTariffReferenceCommand> captor =
                ArgumentCaptor.forClass(FlagStaleTariffReferenceCommand.class);
        verify(mediator, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }
}
