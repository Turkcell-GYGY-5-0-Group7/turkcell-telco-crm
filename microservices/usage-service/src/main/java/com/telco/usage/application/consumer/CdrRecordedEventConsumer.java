package com.telco.usage.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.usage.application.command.MeterCdrCommand;
import com.telco.usage.domain.UsageType;
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
 * Consumes {@code cdr-recorded.v1} events from the {@code cdr.events} Kafka topic and
 * dispatches {@link MeterCdrCommand} via the mediator (ADR-008).
 *
 * <p>Idempotency is handled at two levels:
 * <ol>
 *   <li>Inbox dedup keyed on {@code cdrRef} from the payload (CDR simulator may not set Kafka key).</li>
 *   <li>The handler itself performs a fast-path {@code existsByCdrRef} check before locking the quota.</li>
 * </ol>
 *
 * <p>The consumer is skipped when the {@code cdr-sim} profile is active (simulator produces; does not consume).
 */
@Component
public class CdrRecordedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CdrRecordedEventConsumer.class);
    private static final String CONSUMER_NAME = "CdrRecordedEventConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public CdrRecordedEventConsumer(Mediator mediator,
                                    InboxService inboxService,
                                    ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "cdr.events", groupId = "usage-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onCdrRecorded(ConsumerRecord<String, String> record) {
        String messageId;
        CdrPayload payload;

        try {
            payload = objectMapper.readValue(record.value(), CdrPayload.class);
            // Use cdrRef from payload as the dedup key; it is the CDR's natural idempotency key.
            messageId = payload.cdrRef() != null
                    ? payload.cdrRef()
                    : "fallback-offset-" + record.offset();
        } catch (Exception e) {
            LOGGER.error("Failed to parse CDR payload offset={}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("cdr.events payload parse failed", e);
        }

        try {
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate CDR messageId={}", messageId);
                return;
            }

            if (payload.subscriptionId() == null || payload.type() == null || payload.cdrRef() == null) {
                LOGGER.warn("Ignoring incomplete CDR payload messageId={}", messageId);
                return;
            }

            Instant occurredAt = payload.occurredAt() != null
                    ? Instant.parse(payload.occurredAt())
                    : Instant.now();

            MeterCdrCommand command = new MeterCdrCommand(
                    UUID.fromString(payload.subscriptionId()),
                    UsageType.valueOf(payload.type().toUpperCase()),
                    payload.quantity(),
                    occurredAt,
                    payload.cdrRef());

            mediator.send(command);
            LOGGER.info("Dispatched MeterCdrCommand cdrRef={} subscriptionId={} type={} quantity={}",
                    payload.cdrRef(), payload.subscriptionId(), payload.type(), payload.quantity());

        } catch (Exception e) {
            LOGGER.error("Failed to process CDR messageId={}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("cdr.events processing failed", e);
        }
    }

    /**
     * JSON payload DTO for {@code cdr.events} messages produced by the CDR simulator.
     * {@code occurredAt} is an ISO-8601 instant string.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CdrPayload(
            String subscriptionId,
            String type,
            long quantity,
            String occurredAt,
            String cdrRef
    ) {
    }
}
