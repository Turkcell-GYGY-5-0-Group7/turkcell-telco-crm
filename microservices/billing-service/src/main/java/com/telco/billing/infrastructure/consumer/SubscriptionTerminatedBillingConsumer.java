package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionTerminatedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class SubscriptionTerminatedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTerminatedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionTerminatedBillingConsumer";
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "subscription.terminated.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionTerminatedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                 ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "billing-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionTerminated(ConsumerRecord<String, String> record) {
        // Type filter FIRST (fail closed): subscription.events carries every subscription event type;
        // only subscription.terminated.v1 is handled here.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null) return;

            // Dedup on the stable envelope eventId (ADR-009); fall back only if absent.
            String messageId = payload.eventId() != null ? payload.eventId()
                    : (record.key() != null ? record.key() : "fallback-offset-" + record.offset());

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.terminated.v1 messageId={}", messageId);
                return;
            }

            Instant terminatedAt = payload.terminatedAt() != null
                    ? Instant.parse(payload.terminatedAt()) : Instant.now();

            mediator.send(new RecordSubscriptionTerminatedCommand(
                    UUID.fromString(payload.subscriptionId()), terminatedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.terminated.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("subscription.terminated.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String eventId, String subscriptionId, String terminatedAt) {}
}
