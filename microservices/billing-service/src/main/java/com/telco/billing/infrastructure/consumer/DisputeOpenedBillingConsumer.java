package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.PlaceInvoiceOnDisputeHoldCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumes {@code dispute.opened.v1} from {@code dispute.events} and places a provisional hold on
 * the referenced invoice (ADR-028 Section 5). Own dedicated {@code groupId}, matching
 * {@code SubscriptionSuspendedBillingConsumer}'s precedent (a shared-groupId starvation bug found via
 * live acceptance testing, 2026-07-06) for the three consumers sharing this topic.
 */
@Component
public class DisputeOpenedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeOpenedBillingConsumer.class);
    private static final String CONSUMER_NAME = "DisputeOpenedBillingConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.opened.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public DisputeOpenedBillingConsumer(Mediator mediator, InboxService inboxService, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "dispute.events", groupId = "billing-service-dispute-opened",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeOpened(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.invoiceId() == null) {
                // Payment-originated dispute with no invoice reference - nothing for billing-service to hold.
                return;
            }

            String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate dispute.opened.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new PlaceInvoiceOnDisputeHoldCommand(UUID.fromString(payload.invoiceId())));
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.opened.v1 key={} offset={}", record.key(), record.offset(), e);
            throw new RuntimeException("dispute.opened.v1 billing consumer failed", e);
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String invoiceId) {}
}
