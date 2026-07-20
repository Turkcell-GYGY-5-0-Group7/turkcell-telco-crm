package com.telco.order.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.FulfillOrderCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Fulfils PLAN_CHANGE and ADDON orders (FR-09) on {@code subscription.tariff-changed.v1} /
 * {@code subscription.addon-attached.v1}. These orders are created CONFIRMED (no payment leg), so
 * the provisioning event is the saga's terminal signal, playing the role
 * {@code subscription.activated.v1} plays for NEW_LINE orders.
 *
 * <p>Distinct consumer group from {@link SubscriptionActivatedEventConsumer}'s {@code order-service}
 * group: two listeners in one group on the same topic can starve each other of partitions (the
 * billing-service Sprint 14 live-found bug; see {@code SubscriptionSuspendedBillingConsumer}).
 * Idempotency: {@link FulfillOrderCommand} is an {@code IdempotentRequest} keyed on the Kafka
 * record key; the platform {@code InboxBehavior} dedups redelivery inside the handler transaction.
 */
@Component
public class SubscriptionProvisionedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionProvisionedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final Set<String> FULFILLING_EVENT_TYPES =
            Set.of("subscription.tariff-changed.v1", "subscription.addon-attached.v1");

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public SubscriptionProvisionedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "order-service-subscription-provisioning",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionProvisioned(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (eventType == null || !FULFILLING_EVENT_TYPES.contains(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring {} without orderId messageId={}", eventType, messageId);
                return;
            }

            mediator.send(new FulfillOrderCommand(
                    UUID.fromString(payload.orderId()), payload.subscriptionId(), messageId));
            LOGGER.info("{} processed messageId={} orderId={}", eventType, messageId, payload.orderId());
        } catch (Exception e) {
            LOGGER.error("Failed to process {} messageId={}: {}", eventType, messageId, e.getMessage(), e);
            throw new RuntimeException(eventType + " processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Shared minimal JSON shape of both fulfilling events; unknown fields ignored (ADR-019). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String orderId, String subscriptionId) {
    }
}
