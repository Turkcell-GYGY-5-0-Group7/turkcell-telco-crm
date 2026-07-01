package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.application.dto.SubscriptionActivationFailedPayload;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.domain.repository.PaymentRepository;
import com.telco.platform.inbox.InboxBehavior;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Compensation consumer for the onboarding saga (Feature 9.4.3): consumes
 * {@code subscription.activation-failed.v1} from the {@code subscription.events} topic and refunds
 * the COMPLETED payment for the failed order, dispatching the existing {@link RefundPaymentCommand}
 * via the mediator (ADR-008). The refund emits {@code payment.refunded.v1}, continuing the
 * compensation chain {@code subscription.activation-failed.v1 -> payment.refunded.v1 ->
 * order.cancelled.v1}.
 *
 * <p>Event-type filtering: {@code subscription.events} carries every subscription event type
 * (activated / suspended / terminated / activation-failed), and several share identifying fields.
 * The consumer therefore discriminates on the canonical {@code eventType} Kafka header the Debezium
 * {@code EventRouter} populates from the outbox {@code event_type} column. Only
 * {@code subscription.activation-failed.v1} is acted on; any other type - or a message with no
 * {@code eventType} header - is ignored (fail closed). Refunding on the wrong event would be a
 * data-corruption bug, so payload shape alone is never used to decide.
 *
 * <p><b>Atomic idempotency (ADR-005).</b> Deduplication is NOT done here in the (non-transactional)
 * consumer method. The Kafka record key is carried into {@link RefundPaymentCommand} as its
 * {@code messageId}, and the platform {@link InboxBehavior} inserts the inbox row INSIDE the refund
 * transaction so the inbox row, the payment write and the {@code payment.refunded.v1} outbox event
 * commit or roll back together. The earlier manual {@code firstSeen} commit before the command is
 * removed.
 *
 * <p>Remaining edge cases are resolved BEFORE dispatch, as pure read-side guards:
 * <ul>
 *   <li>No payment for the order, or a payment that is not in the refundable COMPLETED state
 *       (already REFUNDED, FAILED, or PENDING), is a safe no-op: it is logged and nothing is
 *       dispatched or thrown. Because no command is sent, no inbox row is written - which is correct
 *       under atomic inbox semantics, and a redelivery simply re-evaluates the same guard to the same
 *       no-op. This keeps a second activation failure with a fresh dedup key idempotent even after the
 *       refund already happened, instead of letting the handler throw on an illegal
 *       COMPLETED->REFUNDED transition.</li>
 *   <li>The {@link com.telco.payment.application.handler.RefundPaymentCommandHandler} itself
 *       enforces COMPLETED->REFUNDED, providing defense in depth.</li>
 * </ul>
 */
@Component
public class SubscriptionActivationFailedEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionActivationFailedEventConsumer.class);
    private static final String REFUND_REASON = "SAGA_ACTIVATION_FAILED";
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ACTIVATION_FAILED_EVENT_TYPE = "subscription.activation-failed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;

    public SubscriptionActivationFailedEventConsumer(Mediator mediator,
                                                     ObjectMapper objectMapper,
                                                     PaymentRepository paymentRepository) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(topics = "subscription.events", groupId = "payment-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivationFailed(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only subscription.activation-failed.v1 triggers a refund. Other
        // subscription events on this topic (activated / suspended / terminated) must be ignored.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ACTIVATION_FAILED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            SubscriptionActivationFailedPayload payload =
                    objectMapper.readValue(record.value(), SubscriptionActivationFailedPayload.class);

            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring subscription.activation-failed.v1 without orderId messageId={}",
                        messageId);
                return;
            }

            UUID orderId = UUID.fromString(payload.orderId());
            Optional<Payment> payment = paymentRepository.findByOrderId(orderId);

            if (payment.isEmpty()) {
                // No payment was ever taken for this order -> nothing to compensate. Safe no-op:
                // no command is dispatched, so no inbox row is written; do NOT throw.
                LOGGER.info("No payment found for orderId={} (reason={}); nothing to refund, treating "
                        + "as no-op messageId={}", orderId, payload.reason(), messageId);
                return;
            }

            Payment existing = payment.get();
            if (existing.getStatus() != PaymentStatus.COMPLETED) {
                // Already refunded, or never completed (FAILED / PENDING) -> not refundable. Safe
                // no-op so a fresh-key redelivery after a prior refund does not throw on an illegal
                // COMPLETED->REFUNDED transition.
                LOGGER.info("Payment {} for orderId={} is in status {} (not COMPLETED); treating "
                        + "activation-failed as no-op messageId={}",
                        existing.getId(), orderId, existing.getStatus(), messageId);
                return;
            }

            // Inbox dedup of duplicate deliveries of THIS message happens atomically inside the
            // refund transaction via InboxBehavior, keyed on messageId.
            mediator.send(new RefundPaymentCommand(existing.getId(), REFUND_REASON, messageId));
            LOGGER.info("Saga compensation: refunded paymentId={} for orderId={} reason={} messageId={}",
                    existing.getId(), orderId, payload.reason(), messageId);

        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.activation-failed.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            // Re-throw so Spring Kafka retries (or sends to DLT if configured).
            throw new RuntimeException("subscription.activation-failed.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
