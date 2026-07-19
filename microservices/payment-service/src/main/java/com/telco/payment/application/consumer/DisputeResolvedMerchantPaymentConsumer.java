package com.telco.payment.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.ClearPaymentDisputedCommand;
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
 * Consumes {@code dispute.resolved-merchant.v1} from {@code dispute.events} and clears the disputed
 * flag on the referenced payment with no financial change (ADR-028 Section 5).
 */
@Component
public class DisputeResolvedMerchantPaymentConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisputeResolvedMerchantPaymentConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "dispute.resolved-merchant.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public DisputeResolvedMerchantPaymentConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "dispute.events", groupId = "payment-service-dispute-resolved-merchant",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDisputeResolvedMerchant(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring dispute event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.paymentId() == null) {
                return;
            }

            mediator.send(new ClearPaymentDisputedCommand(UUID.fromString(payload.paymentId()), messageId));
            LOGGER.info("Payment disputed flag cleared paymentId={} messageId={}",
                    payload.paymentId(), messageId);
        } catch (Exception e) {
            LOGGER.error("Failed to process dispute.resolved-merchant.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("dispute.resolved-merchant.v1 payment consumer failed", e);
        }
    }

    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String disputeId, String paymentId) {}
}
