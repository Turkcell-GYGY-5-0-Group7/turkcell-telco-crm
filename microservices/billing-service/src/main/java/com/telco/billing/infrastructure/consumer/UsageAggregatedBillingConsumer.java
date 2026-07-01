package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordOverageCommand;
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
public class UsageAggregatedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageAggregatedBillingConsumer.class);
    private static final String CONSUMER_NAME = "UsageAggregatedBillingConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public UsageAggregatedBillingConsumer(Mediator mediator, InboxService inboxService,
                                          ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "usage.events", groupId = "billing-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onUsageAggregated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.periodStart() == null) return;

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate usage.aggregated.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new RecordOverageCommand(
                    UUID.fromString(payload.subscriptionId()),
                    Instant.parse(payload.periodStart()),
                    Instant.parse(payload.periodEnd()),
                    payload.voiceOverageSeconds(),
                    payload.smsOverageCount(),
                    payload.dataOverageKb(),
                    payload.aggregatedAt() != null ? Instant.parse(payload.aggregatedAt()) : Instant.now()));
        } catch (Exception e) {
            LOGGER.error("Failed to process usage.aggregated.v1 messageId={}", messageId, e);
            throw new RuntimeException("usage.aggregated.v1 billing consumer failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            String subscriptionId,
            String periodStart,
            String periodEnd,
            long voiceOverageSeconds,
            long smsOverageCount,
            long dataOverageKb,
            String aggregatedAt
    ) {}
}
