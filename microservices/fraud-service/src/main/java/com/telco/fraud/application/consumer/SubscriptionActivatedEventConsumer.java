package com.telco.fraud.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.fraud.application.command.IngestLifecycleSignalCommand;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code subscription.activated.v1} (initial activation and manual reactivation both publish
 * it) from the shared {@code subscription.events} topic and appends a {@code SUBSCRIPTION_ACTIVATED}
 * row to the lifecycle log (Feature 23.2.1) - a reactivation transition for the
 * {@code SUSPEND_REACTIVATE_VELOCITY} rule. Idempotent via {@code starter-inbox}; maps the envelope
 * onto {@link IngestLifecycleSignalCommand} and dispatches it through the mediator (ADR-008). Own
 * consumer group for topic fan-out (see {@link MsisdnAllocatedEventConsumer}).
 */
@Component
public class SubscriptionActivatedEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionActivatedEventConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionActivatedEventConsumer";
    private static final String EVENT_TYPE = "subscription.activated.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionActivatedEventConsumer(Mediator mediator, InboxService inboxService,
                                              ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "fraud-service-subscription-activated",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivated(ConsumerRecord<String, String> record) {
        if (!EVENT_TYPE.equals(ConsumerRecords.eventType(record))) {
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null) {
                LOGGER.warn("Ignoring incomplete subscription.activated.v1 key={}", record.key());
                return;
            }

            String messageId = ConsumerRecords.messageId(record, payload.eventId());
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate subscription.activated.v1 messageId={}", messageId);
                return;
            }

            Instant occurredAt = payload.activatedAt() != null
                    ? Instant.ofEpochMilli(payload.activatedAt()) : Instant.now();

            mediator.send(new IngestLifecycleSignalCommand(
                    MsisdnLifecycleEventType.SUBSCRIPTION_ACTIVATED,
                    payload.customerId() != null ? UUID.fromString(payload.customerId()) : null,
                    payload.msisdn(),
                    UUID.fromString(payload.subscriptionId()),
                    occurredAt,
                    null));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.activated.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("subscription.activated.v1 processing failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String eventId, String subscriptionId, String customerId,
                          String msisdn, Long activatedAt) {
    }
}
