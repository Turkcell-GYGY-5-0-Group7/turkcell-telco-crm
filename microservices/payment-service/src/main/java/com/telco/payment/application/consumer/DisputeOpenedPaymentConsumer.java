package com.telco.payment.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.MarkPaymentDisputedCommand;
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
 * Consumes {@code dispute.opened.v1} from {@code dispute.events} and marks the referenced payment
 * disputed (ADR-028 Section 5), suppressing {@code PaymentRetryScheduler}.
 *
 * <p><b>Atomic idempotency (ADR-005, this service's convention - NOT billing-service's manual
 * {@code InboxService.firstSeen}).</b> The Kafka record key is carried into
 * {@link MarkPaymentDisputedCommand} as its {@code messageId}; the platform {@code InboxBehavior}
 * inserts the inbox row INSIDE the handler transaction.
 */
@Component
public class DisputeOpenedPaymentConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeOpenedPaymentConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.opened.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public DisputeOpenedPaymentConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "dispute.events", groupId = "payment-service-dispute-opened",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeOpened(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.paymentId() == null) {
                // Invoice-only dispute with no payment reference yet - nothing for payment-service to mark.
                return;
            }

            mediator.send(new MarkPaymentDisputedCommand(UUID.fromString(payload.paymentId()), messageId));
            LOGGER.info("Payment marked disputed paymentId={} messageId={}", payload.paymentId(), messageId);
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.opened.v1 messageId={}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("dispute.opened.v1 payment consumer failed", e);
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String paymentId) {}
}
