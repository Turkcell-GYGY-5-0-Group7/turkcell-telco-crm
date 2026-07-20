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
 * Consumes {@code msisdn.released.v1} from the shared {@code subscription.events} topic and appends an
 * {@code MSISDN_RELEASED} row to the lifecycle log (Feature 23.2.1). {@code customerId} is nullable on
 * this event for older producers (ADR-029 Amendment 1); the consumer forwards whatever is present and
 * the ingest handler backfills a missing value from the most recent prior allocation. Idempotent via
 * {@code starter-inbox}; maps the envelope onto {@link IngestLifecycleSignalCommand} and dispatches it
 * through the mediator (ADR-008). Own consumer group for topic fan-out (see
 * {@link MsisdnAllocatedEventConsumer}).
 */
@Component
public class MsisdnReleasedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsisdnReleasedEventConsumer.class);
    private static final String CONSUMER_NAME = "MsisdnReleasedEventConsumer";
    private static final String EVENT_TYPE = "msisdn.released.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public MsisdnReleasedEventConsumer(Mediator mediator, InboxService inboxService,
                                       ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "fraud-service-msisdn-released",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onMsisdnReleased(ConsumerRecord<String, String> record) {
        if (!EVENT_TYPE.equals(ConsumerRecords.eventType(record))) {
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.msisdn() == null) {
                LOGGER.warn("Ignoring incomplete msisdn.released.v1 key={}", record.key());
                return;
            }

            String messageId = ConsumerRecords.messageId(record, payload.eventId());
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate msisdn.released.v1 messageId={}", messageId);
                return;
            }

            Instant occurredAt = payload.releasedAt() != null
                    ? Instant.ofEpochMilli(payload.releasedAt()) : Instant.now();

            mediator.send(new IngestLifecycleSignalCommand(
                    MsisdnLifecycleEventType.MSISDN_RELEASED,
                    payload.customerId() != null ? UUID.fromString(payload.customerId()) : null,
                    payload.msisdn(),
                    UUID.fromString(payload.subscriptionId()),
                    occurredAt,
                    null));
        } catch (Exception e) {
            LOGGER.error("Failed to process msisdn.released.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("msisdn.released.v1 processing failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String eventId, String msisdn, String subscriptionId,
                          String customerId, Long releasedAt) {
    }
}
