package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordAddonChargeCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code subscription.addon-attached.v1} (FR-09) and records the addon fee for the next
 * bill-run's ADDON/VAS invoice line (FR-22). Manual-inbox convention (type filter FIRST,
 * {@code firstSeen} dedup, dedicated group). {@code attachedAt} is epoch millis, matching the
 * subscription-service producer and the Avro timestamp-millis contract - NOT an ISO string
 * (the SubscriptionSuspendedBillingConsumer defect class).
 */
@Component
public class SubscriptionAddonAttachedBillingConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionAddonAttachedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionAddonAttachedBillingConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "subscription.addon-attached.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionAddonAttachedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                    ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "billing-service-subscription-addon-attached",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onAddonAttached(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.orderId() == null
                    || payload.addonCode() == null || payload.price() == null) {
                LOGGER.warn("Ignoring subscription.addon-attached.v1 missing fields key={}", record.key());
                return;
            }

            String messageId = record.key() != null ? record.key()
                    : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.addon-attached.v1 messageId={}", messageId);
                return;
            }

            Instant attachedAt = payload.attachedAt() != null
                    ? Instant.ofEpochMilli(payload.attachedAt()) : Instant.now();

            mediator.send(new RecordAddonChargeCommand(
                    UUID.fromString(payload.subscriptionId()),
                    UUID.fromString(payload.orderId()),
                    payload.addonCode(),
                    payload.addonType(),
                    payload.price(),
                    payload.currency() == null ? "TRY" : payload.currency(),
                    attachedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.addon-attached.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("subscription.addon-attached.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Minimal JSON shape; unknown fields ignored (ADR-019). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String subscriptionId, String orderId, String addonCode, String addonType,
                   BigDecimal price, String currency, Long attachedAt) {
    }
}
