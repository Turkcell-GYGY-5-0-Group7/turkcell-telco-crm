package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionTerminatedCommand;
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
public class SubscriptionTerminatedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTerminatedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionTerminatedBillingConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionTerminatedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                 ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.terminated", groupId = "billing-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionTerminated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null) return;

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.terminated.v1 messageId={}", messageId);
                return;
            }

            Instant terminatedAt = payload.terminatedAt() != null
                    ? Instant.parse(payload.terminatedAt()) : Instant.now();

            mediator.send(new RecordSubscriptionTerminatedCommand(
                    UUID.fromString(payload.subscriptionId()), terminatedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.terminated.v1 messageId={}", messageId, e);
            throw new RuntimeException("subscription.terminated.v1 billing consumer failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String subscriptionId, String terminatedAt) {}
}
