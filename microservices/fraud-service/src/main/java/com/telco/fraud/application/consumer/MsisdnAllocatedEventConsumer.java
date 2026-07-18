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
 * Consumes {@code msisdn.allocated.v1} from the shared {@code subscription.events} topic and appends an
 * {@code MSISDN_ALLOCATED} row to the lifecycle log (Feature 23.2.1). Idempotent via
 * {@code starter-inbox}; the consumer only maps the envelope onto {@link IngestLifecycleSignalCommand}
 * and dispatches it through the mediator (ADR-008) - all persistence and rule evaluation live in the
 * handler chain.
 *
 * <p>Own consumer group (fan-out, not competing consumption): {@code subscription.events} carries every
 * subscription-aggregate event type, so each fraud consumer needs its own group id to see every message
 * and filter to its own {@code eventType} header - sharing one group would let the group coordinator
 * starve all but one listener of the topic's partition (the billing-service consumer defect, 2026-07-06).
 */
@Component
public class MsisdnAllocatedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsisdnAllocatedEventConsumer.class);
    private static final String CONSUMER_NAME = "MsisdnAllocatedEventConsumer";
    private static final String EVENT_TYPE = "msisdn.allocated.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public MsisdnAllocatedEventConsumer(Mediator mediator, InboxService inboxService,
                                        ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "fraud-service-msisdn-allocated",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onMsisdnAllocated(ConsumerRecord<String, String> record) {
        // Type filter FIRST (fail closed), before touching the inbox: the shared topic carries every
        // subscription event type and they share the aggregate-id record key, so an unrelated event
        // must not consume this consumer's dedup slot.
        if (!EVENT_TYPE.equals(ConsumerRecords.eventType(record))) {
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.msisdn() == null) {
                LOGGER.warn("Ignoring incomplete msisdn.allocated.v1 key={}", record.key());
                return;
            }

            String messageId = ConsumerRecords.messageId(record, payload.eventId());
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate msisdn.allocated.v1 messageId={}", messageId);
                return;
            }

            Instant occurredAt = payload.allocatedAt() != null
                    ? Instant.ofEpochMilli(payload.allocatedAt()) : Instant.now();

            mediator.send(new IngestLifecycleSignalCommand(
                    MsisdnLifecycleEventType.MSISDN_ALLOCATED,
                    payload.customerId() != null ? UUID.fromString(payload.customerId()) : null,
                    payload.msisdn(),
                    UUID.fromString(payload.subscriptionId()),
                    occurredAt,
                    null));
        } catch (Exception e) {
            LOGGER.error("Failed to process msisdn.allocated.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("msisdn.allocated.v1 processing failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String eventId, String msisdn, String subscriptionId,
                          String customerId, Long allocatedAt) {
    }
}
