package com.telco.ticket.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.OpenTicketCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Consumes {@code fraud.case-opened.v1} from the shared {@code fraud.events} topic (fraud-service,
 * ADR-029 Section 5) and auto-opens a fraud-review {@link OpenTicketCommand} so an agent can act on the
 * case through ticket-service's existing SLA/assignment workflow (Feature 23.4.2). This mirrors the
 * ADR-028 {@code dispute.opened.v1} -> ticket precedent: the consumer reuses the existing
 * {@code OpenTicketCommandHandler} (no parallel ticketing path), so {@code SlaPolicy} auto-assignment
 * and the {@code ticket.opened.v1}/{@code ticket.assigned.v1} outbox events apply unchanged.
 *
 * <p>Detect-and-alert only (ADR-029 Section 5): opening the ticket is agent-facing work; it never calls
 * subscription-service and never suspends a subscription.
 *
 * <p>Idempotent via {@code starter-inbox}: the shared {@code fraud.events} topic carries every fraud
 * event type, so this consumer runs on its OWN group id, filters to its {@code eventType} header first
 * (fail closed), then dedups on the envelope {@code eventId} before dispatching - a replayed event
 * opens no second ticket.
 */
@Component
public class FraudCaseOpenedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FraudCaseOpenedEventConsumer.class);
    private static final String CONSUMER_NAME = "FraudCaseOpenedEventConsumer";
    private static final String EVENT_TYPE = "fraud.case-opened.v1";
    /** Free-form ticket category (the taxonomy is a String, not an enum); routed by V2 SLA policy. */
    private static final String CATEGORY = "FRAUD_REVIEW";
    private static final String DEFAULT_PRIORITY = "MEDIUM";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public FraudCaseOpenedEventConsumer(Mediator mediator, InboxService inboxService,
                                        ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "fraud.events", groupId = "ticket-service-fraud-case-opened",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onFraudCaseOpened(ConsumerRecord<String, String> record) {
        // Type filter FIRST (fail closed), before touching the inbox: the shared topic carries every
        // fraud event type, so an unrelated event must not consume this consumer's dedup slot.
        if (!EVENT_TYPE.equals(ConsumerRecords.eventType(record))) {
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.caseId() == null || payload.customerId() == null) {
                LOGGER.warn("Ignoring incomplete fraud.case-opened.v1 key={}", record.key());
                return;
            }

            String messageId = ConsumerRecords.messageId(record, payload.eventId());
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate fraud.case-opened.v1 messageId={}", messageId);
                return;
            }

            String priority = payload.highestSeverity() != null
                    ? payload.highestSeverity().toUpperCase(Locale.ROOT) : DEFAULT_PRIORITY;
            String subject = "Fraud case review: " + payload.caseId()
                    + " (severity " + priority + ")";

            mediator.send(new OpenTicketCommand(
                    UUID.fromString(payload.customerId()),
                    CATEGORY,
                    priority,
                    subject,
                    payload.caseId()));

            LOGGER.info("Auto-opened fraud-review ticket for caseId={} customerId={} severity={}",
                    payload.caseId(), payload.customerId(), priority);
        } catch (Exception e) {
            LOGGER.error("Failed to process fraud.case-opened.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("fraud.case-opened.v1 processing failed", e);
        }
    }

    /**
     * JSON projection of the {@code fraud.case-opened.v1} outbox payload (the canonical
     * {@code FraudCaseOpenedV1} shape plus the envelope {@code eventId} the outbox serializer embeds).
     * Unknown fields ignored so envelope metadata does not break deserialization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String eventId, String caseId, String customerId, List<String> signalIds,
                          Long openedAt, String highestSeverity) {
    }
}
