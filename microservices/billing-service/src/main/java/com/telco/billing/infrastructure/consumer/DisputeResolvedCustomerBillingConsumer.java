package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.ApplyDisputeAdjustmentCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
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
 * Consumes {@code dispute.resolved-customer.v1} from {@code dispute.events}. If the event carries an
 * {@code invoiceId}, dispatches {@link ApplyDisputeAdjustmentCommand} - the handler (and
 * {@code Invoice.applyDisputeAdjustment}'s own check-then-act guard) safely no-ops if the invoice is
 * already PAID (payment-service's refund path owns that case, Feature 22.5.3) or the hold is not set.
 */
@Component
public class DisputeResolvedCustomerBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeResolvedCustomerBillingConsumer.class);
    private static final String CONSUMER_NAME = "DisputeResolvedCustomerBillingConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.resolved-customer.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public DisputeResolvedCustomerBillingConsumer(Mediator mediator, InboxService inboxService,
                                                  ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "dispute.events", groupId = "billing-service-dispute-resolved-customer",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeResolvedCustomer(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.invoiceId() == null) {
                // Payment-only dispute - payment-service's refund path owns this (Feature 22.5.3).
                return;
            }

            String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate dispute.resolved-customer.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new ApplyDisputeAdjustmentCommand(
                    UUID.fromString(payload.invoiceId()), payload.resolutionAmount()));
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.resolved-customer.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("dispute.resolved-customer.v1 billing consumer failed", e);
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String invoiceId, BigDecimal resolutionAmount) {}
}
