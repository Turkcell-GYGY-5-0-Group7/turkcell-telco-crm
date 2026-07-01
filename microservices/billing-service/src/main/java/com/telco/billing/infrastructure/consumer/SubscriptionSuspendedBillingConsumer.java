package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionSuspendedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SubscriptionSuspendedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionSuspendedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionSuspendedBillingConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionSuspendedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.suspended", groupId = "billing-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionSuspended(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null) return;

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.suspended.v1 messageId={}", messageId);
                return;
            }

            Instant suspendedAt = payload.suspendedAt() != null
                    ? Instant.parse(payload.suspendedAt()) : Instant.now();

            mediator.send(new RecordSubscriptionSuspendedCommand(
                    UUID.fromString(payload.subscriptionId()), suspendedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.suspended.v1 messageId={}", messageId, e);
            throw new RuntimeException("subscription.suspended.v1 billing consumer failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String subscriptionId, String suspendedAt) {}
}
