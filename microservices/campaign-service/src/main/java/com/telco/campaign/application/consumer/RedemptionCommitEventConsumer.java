package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.ConfirmRedemptionCommand;
import com.telco.campaign.application.dto.PaymentCompletedPayload;
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
 * Consumes {@code payment.completed.v1} from the {@code payment.events} Kafka topic and confirms the
 * matching {@code CampaignRedemption} (RESERVED -&gt; CONFIRMED) by dispatching
 * {@link ConfirmRedemptionCommand} (Feature 21.4.2, ADR-027 Section 4 ratification: this is the "order
 * is real" trigger, NOT {@code order.confirmed.v1}, which is deferred/never produced).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code payment.events} carries every payment event type
 * ({@code payment.completed.v1}, {@code payment.failed.v1}, {@code payment.refunded.v1}). This
 * consumer discriminates on the canonical {@code eventType} Kafka header the Debezium
 * {@code EventRouter} populates from the outbox {@code event_type} column - mirrors order-service's
 * {@code PaymentCompletedEventConsumer} exactly. Only {@code payment.completed.v1} confirms a
 * redemption; any other type - or a message with no {@code eventType} header - is ignored.
 *
 * <p><b>Idempotency (two layers):</b> (1) the dispatched {@link ConfirmRedemptionCommand} is an
 * {@code IdempotentRequest} keyed on the Kafka record key; the platform {@code InboxBehavior} dedups
 * redelivery of the same message ATOMICALLY inside the handler transaction; (2)
 * {@code ConfirmRedemptionCommandHandler} is check-then-act - it only confirms a RESERVED redemption,
 * so an already-CONFIRMED redemption is a no-op even if the dedup key ever changed.
 *
 * <p><b>Distinct consumer group:</b> this is the only campaign-service listener on
 * {@code payment.events}, but per the order-service lesson (multiple @KafkaListener members of the
 * SAME consumer group compete for the topic's partition(s), silently starving one another), this uses
 * a dedicated {@code campaign-service-redemption-commit} group id, never shared with any other
 * campaign-service listener on the same topic.
 */
@Component
public class RedemptionCommitEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedemptionCommitEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String PAYMENT_COMPLETED_EVENT_TYPE = "payment.completed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public RedemptionCommitEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.events", groupId = "campaign-service-redemption-commit",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only payment.completed.v1 confirms a redemption.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!PAYMENT_COMPLETED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring payment event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            PaymentCompletedPayload payload =
                    objectMapper.readValue(record.value(), PaymentCompletedPayload.class);

            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring payment.completed.v1 without orderId messageId={}", messageId);
                return;
            }

            mediator.send(new ConfirmRedemptionCommand(UUID.fromString(payload.orderId()), messageId));

            LOGGER.info("payment.completed.v1 processed for redemption commit messageId={} orderId={}",
                    messageId, payload.orderId());

        } catch (Exception e) {
            LOGGER.error("Failed to process payment.completed.v1 for redemption commit messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("payment.completed.v1 redemption-commit processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
