package com.telco.ticket.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.OpenTicketCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumes {@code dispute.opened.v1} from {@code dispute.events} and auto-opens a linked
 * {@code DISPUTE}-category ticket via the EXISTING {@link OpenTicketCommand}, reusing
 * ticket-service's existing SLA/assignment machinery rather than dispute-service reinventing it
 * (ADR-028 Section 4, reuse-before-build). Manual inbox dedup (billing-service's convention -
 * {@code OpenTicketCommand} is not {@code IdempotentRequest}), own dedicated groupId per the
 * repo's own shared-groupId-starvation precedent.
 */
@Component
public class DisputeOpenedTicketConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeOpenedTicketConsumer.class);
    private static final String CONSUMER_NAME = "DisputeOpenedTicketConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.opened.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public DisputeOpenedTicketConsumer(Mediator mediator, InboxService inboxService, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "dispute.events", groupId = "ticket-service-dispute-opened",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeOpened(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.disputeId() == null || payload.customerId() == null) {
                LOGGER.warn("Ignoring incomplete dispute.opened.v1 payload key={}", record.key());
                return;
            }

            String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate dispute.opened.v1 messageId={}", messageId);
                return;
            }

            String priority = priorityFor(payload.disputedAmount());
            mediator.send(new OpenTicketCommand(
                    UUID.fromString(payload.customerId()),
                    "DISPUTE",
                    priority,
                    "Dispute opened: " + payload.disputeId(),
                    payload.disputeId()));

            LOGGER.info("Auto-opened DISPUTE ticket for disputeId={} customerId={}",
                    payload.disputeId(), payload.customerId());
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.opened.v1 key={} offset={}", record.key(), record.offset(), e);
            throw new RuntimeException("dispute.opened.v1 ticket consumer failed", e);
        }
    }

    private static String priorityFor(BigDecimal disputedAmount) {
        if (disputedAmount == null) {
            return "MEDIUM";
        }
        if (disputedAmount.compareTo(new BigDecimal("1000")) >= 0) {
            return "HIGH";
        }
        if (disputedAmount.compareTo(new BigDecimal("100")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String customerId, BigDecimal disputedAmount) {}
}
