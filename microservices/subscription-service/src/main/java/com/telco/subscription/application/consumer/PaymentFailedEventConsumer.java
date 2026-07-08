package com.telco.subscription.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.SuspendSubscriptionsForNonPaymentCommand;
import com.telco.subscription.application.dto.PaymentFailedPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumes {@code payment.failed.v1} from the {@code payment.events} Kafka topic and suspends the
 * customer's ACTIVE subscriptions for non-payment (FR-14, saga suspend path).
 *
 * <p>Event-type filtering: {@code payment.events} carries every payment event type
 * ({@code payment.completed.v1}, {@code payment.refunded.v1}, {@code payment.failed.v1}), and all of
 * them include a {@code customerId}. The consumer therefore discriminates on the canonical
 * {@code eventType} Kafka header that the Debezium {@code EventRouter} populates from the outbox
 * {@code event_type} column (see {@code infra/docker/kafka-connect/connectors/outbox-connector.example.json},
 * {@code table.field.event.type=event_type}). Only {@code payment.failed.v1} is acted on; any other
 * type - or a message with no {@code eventType} header - is ignored. Suspending on a successful
 * payment would be a data-corruption bug, so payload shape alone is never used to decide.
 *
 * <p>Idempotency is enforced in two layers: (1) the {@link SuspendSubscriptionsForNonPaymentCommand}
 * is an {@code IdempotentRequest} keyed on the Kafka record key (the outbox event id set by the
 * relay), so the mediator {@code InboxBehavior} dedups a redelivered record INSIDE the handler
 * transaction (ADR-005) and skips it; and (2) the command handler only suspends ACTIVE subscriptions,
 * making a re-run a no-op even if the dedup key changes.
 *
 * <p>Grace period (simplification, follow-up): the spec calls for suspending only AFTER a grace
 * period (24/72h). payment-service already owns the 24/72/168h retry scheduler that decides when a
 * payment is finally failed; this consumer therefore treats receipt of {@code payment.failed.v1} as
 * the post-grace signal and suspends immediately. A subscription-side timed deferral (holding the
 * suspend for N hours and cancelling it if a later {@code payment.completed.v1} arrives) is deferred
 * to the saga wiring in 9.4 / a follow-up, and is intentionally NOT faked here.
 *
 * <p><b>Distinct consumer group (bug found via live acceptance testing, 2026-07-06):</b> this
 * listener and {@link PaymentCompletedEventConsumer} both read {@code payment.events}, and both
 * previously used the shared {@code groupId="subscription-service"}. Two @KafkaListener members of
 * the SAME consumer group compete for the topic's partition(s): Kafka's group coordinator hands the
 * (single, dev-sized) partition to exactly one member, permanently starving the other of every
 * message on the topic, not just the event types it does not care about. Each consumer here filters
 * internally by {@code eventType} and is meant to see every message independently (fan-out, not
 * competing consumption), so each now gets its own dedicated group id.
 */
@Component
public class PaymentFailedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentFailedEventConsumer.class);
    private static final String SUSPEND_REASON = "NON_PAYMENT";
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String PAYMENT_FAILED_EVENT_TYPE = "payment.failed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public PaymentFailedEventConsumer(Mediator mediator,
                                      ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.events", groupId = "subscription-service-payment-failed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only payment.failed.v1 may suspend a line. payment.completed.v1 /
        // payment.refunded.v1 (also on this topic, also carrying customerId) must be ignored.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!PAYMENT_FAILED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring payment event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            PaymentFailedPayload payload = objectMapper.readValue(record.value(), PaymentFailedPayload.class);

            if (payload.customerId() == null) {
                LOGGER.warn("Ignoring payment.failed.v1 without customerId messageId={}", messageId);
                return;
            }

            // Dedup is delegated to the mediator: the command is an IdempotentRequest keyed by
            // messageId, so InboxBehavior skips a redelivered record inside the handler transaction.
            // A skipped (already-processed) command returns null - treat it as zero suspensions.
            Integer suspended = mediator.send(new SuspendSubscriptionsForNonPaymentCommand(
                    UUID.fromString(payload.customerId()), SUSPEND_REASON, messageId));

            LOGGER.info("payment.failed.v1 processed messageId={} customerId={} suspendedCount={}",
                    messageId, payload.customerId(), suspended == null ? 0 : suspended);

        } catch (Exception e) {
            LOGGER.error("Failed to process payment.failed.v1 messageId={}: {}", messageId, e.getMessage(), e);
            // Re-throw so Spring Kafka retries (or sends to DLT if configured).
            throw new RuntimeException("payment.failed.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
