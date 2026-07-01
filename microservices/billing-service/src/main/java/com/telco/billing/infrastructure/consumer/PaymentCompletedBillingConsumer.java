package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.MarkInvoicePaidCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentCompletedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCompletedBillingConsumer.class);
    private static final String CONSUMER_NAME = "PaymentCompletedBillingConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public PaymentCompletedBillingConsumer(Mediator mediator, InboxService inboxService,
                                           ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.events", groupId = "billing-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);

            // Only handle payments that settle an invoice (not order-driven payments).
            if (payload.invoiceId() == null) {
                LOGGER.debug("payment.completed.v1 has no invoiceId — skipping, messageId={}", messageId);
                return;
            }

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate payment.completed.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new MarkInvoicePaidCommand(UUID.fromString(payload.invoiceId())));
        } catch (Exception e) {
            LOGGER.error("Failed to process payment.completed.v1 messageId={}", messageId, e);
            throw new RuntimeException("payment.completed.v1 billing consumer failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String paymentId, String invoiceId) {}
}
