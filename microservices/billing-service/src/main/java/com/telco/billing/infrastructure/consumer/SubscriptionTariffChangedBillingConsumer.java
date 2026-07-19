package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionTariffChangedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumes {@code subscription.tariff-changed.v1} (FR-09) so the billing record follows a plan
 * change and the next bill-run prices the new tariff. This service's manual-inbox consumer
 * convention: type filter FIRST (fail closed), {@code InboxService.firstSeen} dedup, dedicated
 * consumer group (see {@link SubscriptionSuspendedBillingConsumer} for why the group id is never
 * shared across listeners on one topic).
 */
@Component
public class SubscriptionTariffChangedBillingConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionTariffChangedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionTariffChangedBillingConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "subscription.tariff-changed.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionTariffChangedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                    ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "billing-service-subscription-tariff-changed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffChanged(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.newTariffCode() == null) {
                LOGGER.warn("Ignoring subscription.tariff-changed.v1 missing fields key={}", record.key());
                return;
            }

            String messageId = record.key() != null ? record.key()
                    : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.tariff-changed.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new RecordSubscriptionTariffChangedCommand(
                    UUID.fromString(payload.subscriptionId()), payload.newTariffCode()));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.tariff-changed.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("subscription.tariff-changed.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Minimal JSON shape; unknown fields ignored (ADR-019). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String subscriptionId, String newTariffCode) {
    }
}
