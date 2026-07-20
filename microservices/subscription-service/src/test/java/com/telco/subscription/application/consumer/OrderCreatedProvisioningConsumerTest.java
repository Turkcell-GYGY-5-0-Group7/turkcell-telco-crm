package com.telco.subscription.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.AttachAddonCommand;
import com.telco.subscription.application.command.ChangeSubscriptionTariffCommand;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderCreatedProvisioningConsumerTest {

    @Mock
    private Mediator mediator;

    private OrderCreatedProvisioningConsumer consumer;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new OrderCreatedProvisioningConsumer(mediator, new ObjectMapper());
    }

    private ConsumerRecord<String, String> record(String eventType, String payload) {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order.events", 0, 0L, "msg-key-1", payload);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private String orderJson(String orderType, String itemJson) {
        return """
                {"orderId":"%s","customerId":"%s","orderType":%s,"subscriptionId":"%s",
                 "items":[%s],"totalAmount":49.90,"idempotencyKey":"idem-1"}"""
                .formatted(orderId, customerId,
                        orderType == null ? "null" : "\"" + orderType + "\"", subscriptionId, itemJson);
    }

    @Test
    void planChangeOrderDispatchesChangeTariffCommand() {
        consumer.onOrderCreated(record("order.created.v1", orderJson("PLAN_CHANGE",
                "{\"tariffId\":\"" + UUID.randomUUID() + "\",\"tariffCode\":\"TARIFF_PREMIUM\","
                        + "\"unitPrice\":99.90,\"quantity\":1,\"currency\":\"TRY\"}")));

        ArgumentCaptor<ChangeSubscriptionTariffCommand> captor =
                ArgumentCaptor.forClass(ChangeSubscriptionTariffCommand.class);
        verify(mediator).send(captor.capture());
        ChangeSubscriptionTariffCommand command = captor.getValue();
        assertThat(command.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.customerId()).isEqualTo(customerId);
        assertThat(command.newTariffCode()).isEqualTo("TARIFF_PREMIUM");
        assertThat(command.messageId()).isEqualTo("msg-key-1");
    }

    @Test
    void addonOrderDispatchesAttachAddonCommand() {
        consumer.onOrderCreated(record("order.created.v1", orderJson("ADDON",
                "{\"addonCode\":\"DATA_5GB\",\"addonType\":\"DATA\","
                        + "\"unitPrice\":49.90,\"quantity\":1,\"currency\":\"TRY\"}")));

        ArgumentCaptor<AttachAddonCommand> captor = ArgumentCaptor.forClass(AttachAddonCommand.class);
        verify(mediator).send(captor.capture());
        AttachAddonCommand command = captor.getValue();
        assertThat(command.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.customerId()).isEqualTo(customerId);
        assertThat(command.addonCode()).isEqualTo("DATA_5GB");
        assertThat(command.addonType()).isEqualTo("DATA");
        assertThat(command.price()).isEqualByComparingTo("49.90");
        assertThat(command.currency()).isEqualTo("TRY");
        assertThat(command.messageId()).isEqualTo("msg-key-1");
    }

    @Test
    void newLineOrderIsIgnoredHere() {
        consumer.onOrderCreated(record("order.created.v1", orderJson("NEW_LINE",
                "{\"tariffId\":\"" + UUID.randomUUID() + "\",\"unitPrice\":99.90,\"quantity\":1}")));
        verifyNoInteractions(mediator);
    }

    @Test
    void nullOrderTypeFromPreFr09ProducerIsIgnoredHere() {
        consumer.onOrderCreated(record("order.created.v1", orderJson(null,
                "{\"tariffId\":\"" + UUID.randomUUID() + "\",\"unitPrice\":99.90,\"quantity\":1}")));
        verifyNoInteractions(mediator);
    }

    @Test
    void otherEventTypesAreIgnored() {
        consumer.onOrderCreated(record("order.cancelled.v1", orderJson("PLAN_CHANGE",
                "{\"tariffCode\":\"TARIFF_PREMIUM\",\"unitPrice\":99.90,\"quantity\":1}")));
        verifyNoInteractions(mediator);
    }

    @Test
    void planChangeWithoutTariffCodeIsSkippedNotRetried() {
        consumer.onOrderCreated(record("order.created.v1", orderJson("PLAN_CHANGE",
                "{\"tariffId\":\"" + UUID.randomUUID() + "\",\"unitPrice\":99.90,\"quantity\":1}")));
        verify(mediator, never()).send(any());
    }
}
