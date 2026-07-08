package com.telco.order.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.ConfirmOrderCommand;
import com.telco.order.application.dto.PaymentCompletedPayload;
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
 * order (saga step PAYMENT_COMPLETED / status PAID) by dispatching {@link ConfirmOrderCommand}
 * (ADR-008, AC-01).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code payment.events} carries every payment event type
 * ({@code payment.completed.v1}, {@code payment.failed.v1}, {@code payment.refunded.v1}), all sharing
 * a similar JSON shape. The consumer discriminates on the canonical {@code eventType} Kafka header
 * the Debezium {@code EventRouter} populates from the outbox {@code event_type} column. Only
 * {@code payment.completed.v1} confirms an order; any other type - or a message with no
 * {@code eventType} header - is ignored. Confirming on a refund/failure would corrupt the saga, so
 * payload shape alone is never used to decide.
 *
 * <p><b>Idempotency (two layers):</b> (1) the dispatched {@link ConfirmOrderCommand} is an
 * {@code IdempotentRequest} keyed on the Kafka record key; the platform {@code InboxBehavior} dedups
 * redelivery of the same message ATOMICALLY inside the handler transaction (tech-lead ruling 2a/2b);
 * (2) the handler is check-then-act - it only confirms a PENDING order, so an already-CONFIRMED (or
 * further) order is a no-op even if the dedup key changes.
 *
 * <p><b>Distinct consumer group (bug found via live acceptance testing, 2026-07-06):</b> this
 * listener and {@link PaymentRefundedEventConsumer} both read {@code payment.events}, and both
 * previously used the shared {@code groupId="order-service"}. Two @KafkaListener members of the SAME
 * consumer group compete for the topic's partition(s): Kafka's group coordinator hands the (single,
 * dev-sized) partition to exactly one member, permanently starving the other of every message on the
 * topic - not just the event types it does not care about. Each consumer here filters internally by
 * {@code eventType} and is meant to see every message independently (fan-out, not competing
 * consumption), so each now gets its own dedicated group id.
 */
@Component
public class PaymentCompletedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCompletedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String PAYMENT_COMPLETED_EVENT_TYPE = "payment.completed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public PaymentCompletedEventConsumer(Mediator mediator,
                                         ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.events", groupId = "order-service-payment-completed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only payment.completed.v1 confirms an order.
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

            // Atomic inbox dedup: ConfirmOrderCommand is an IdempotentRequest keyed on messageId;
            // InboxBehavior skips redelivery inside the handler transaction.
            mediator.send(new ConfirmOrderCommand(
                    UUID.fromString(payload.orderId()), payload.paymentId(), messageId));

            LOGGER.info("payment.completed.v1 processed messageId={} orderId={}",
                    messageId, payload.orderId());

        } catch (Exception e) {
            LOGGER.error("Failed to process payment.completed.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("payment.completed.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
