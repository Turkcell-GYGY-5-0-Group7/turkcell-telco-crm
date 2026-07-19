package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.dto.OrderCreatedPayload;
import com.telco.payment.domain.PaymentMethod;
import com.telco.platform.inbox.InboxBehavior;
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
 * Consumes {@code order.created.v1} events from the {@code order.events} Kafka topic and
 * dispatches {@link ChargePaymentCommand} via the mediator (ADR-008).
 *
 * <p>Event-type filtering: {@code order.events} carries every order event type. Since the saga
 * compensation path, order-service publishes {@code order.cancelled.v1} on this SAME topic;
 * payment-service must NOT charge on a cancellation. The consumer therefore discriminates on the
 * canonical {@code eventType} Kafka header that the Debezium {@code EventRouter} populates from the
 * outbox {@code event_type} column. Only {@code order.created.v1} is acted on; any other type - or a
 * message with no {@code eventType} header - is ignored (fail closed). Payload shape alone is never
 * used to decide, because every order event carries the same identifying fields.
 *
 * <p><b>Atomic idempotency (ADR-005).</b> Deduplication is NOT done here in the (non-transactional)
 * consumer method. Instead the Kafka record key is carried into {@link ChargePaymentCommand} as its
 * {@code messageId}, and the platform {@link InboxBehavior} inserts the inbox row INSIDE the handler
 * transaction. The inbox row, the payment write and the outbox event therefore commit or roll back
 * together: a charge rollback re-arms redelivery instead of silently dropping the saga step. The
 * earlier pattern (a manual {@code firstSeen} commit in this method, then a separate command) is
 * removed because it committed the inbox row before the charge transaction.
 *
 * <p>The {@code paymentRequestId} is still derived from the {@code orderId} so that
 * {@link com.telco.payment.application.handler.ChargePaymentCommandHandler} can also enforce
 * command-level idempotency independently (FR-25): the inbox guards duplicate <em>deliveries</em>,
 * the {@code paymentRequestId} guards the same order arriving through a different message.
 */
@Component
public class OrderCreatedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ORDER_CREATED_EVENT_TYPE = "order.created.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public OrderCreatedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "payment-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only order.created.v1 may charge. order.cancelled.v1 (also on this
        // topic, saga compensation) must be ignored - charging on a cancellation would be a bug.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ORDER_CREATED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring order event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            OrderCreatedPayload payload = objectMapper.readValue(record.value(), OrderCreatedPayload.class);

            if (payload.orderId() == null || payload.customerId() == null || payload.totalAmount() == null) {
                LOGGER.warn("Ignoring incomplete order.created.v1 payload messageId={}", messageId);
                return;
            }

            // paymentRequestId is derived from orderId for stable command-level idempotency; messageId
            // is the inbox key. Inbox dedup happens atomically inside the handler transaction.
            String paymentRequestId = payload.orderId();

            // order.created.v1 is an order-only saga step; it never targets an invoice. The saga
            // auto-charge always uses CREDIT_CARD - the event carries no method choice (FR-25).
            ChargePaymentCommand command = new ChargePaymentCommand(
                    UUID.fromString(payload.orderId()),
                    UUID.fromString(payload.customerId()),
                    payload.totalAmount(),
                    null,
                    PaymentMethod.CREDIT_CARD,
                    paymentRequestId,
                    messageId);

            mediator.send(command);
            LOGGER.info("Dispatched ChargePaymentCommand for orderId={} messageId={}",
                    payload.orderId(), messageId);

        } catch (Exception e) {
            LOGGER.error("Failed to process order.created.v1 messageId={}: {}", messageId, e.getMessage(), e);
            // Re-throw so Spring Kafka retries (or sends to DLT if configured).
            throw new RuntimeException("order.created.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
