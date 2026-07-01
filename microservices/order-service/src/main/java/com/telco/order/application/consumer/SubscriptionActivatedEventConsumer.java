package com.telco.order.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.FulfillOrderCommand;
import com.telco.order.application.dto.SubscriptionActivatedPayload;
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
 * Consumes {@code subscription.activated.v1} from the {@code subscription.events} Kafka topic and
 * fulfills the order (saga step SUBSCRIPTION_ACTIVATED / status FULFILLED, terminal success) by
 * dispatching {@link FulfillOrderCommand} (ADR-008, AC-01).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code subscription.events} carries every subscription
 * event type ({@code subscription.activated.v1}, {@code subscription.suspended.v1},
 * {@code subscription.terminated.v1}, ...). The consumer discriminates on the canonical
 * {@code eventType} Kafka header the Debezium {@code EventRouter} populates. Only
 * {@code subscription.activated.v1} fulfills an order; any other type - or a message with no
 * {@code eventType} header - is ignored.
 *
 * <p><b>Correlation:</b> the order is identified by the {@code orderId} field now carried on the
 * activation event (nullable union, always populated for saga-driven activations). A message without
 * an {@code orderId} (manual/non-saga activation) is ignored.
 *
 * <p><b>Idempotency (two layers):</b> (1) the dispatched {@link FulfillOrderCommand} is an
 * {@code IdempotentRequest} keyed on the Kafka record key; the platform {@code InboxBehavior} dedups
 * redelivery of the same message ATOMICALLY inside the handler transaction (tech-lead ruling 2a/2b);
 * (2) the handler is check-then-act - it only fulfills a CONFIRMED order, so an already-FULFILLED
 * order is a no-op even if the dedup key changes.
 */
@Component
public class SubscriptionActivatedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionActivatedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String SUBSCRIPTION_ACTIVATED_EVENT_TYPE = "subscription.activated.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public SubscriptionActivatedEventConsumer(Mediator mediator,
                                              ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "order-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only subscription.activated.v1 fulfills an order.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!SUBSCRIPTION_ACTIVATED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            SubscriptionActivatedPayload payload =
                    objectMapper.readValue(record.value(), SubscriptionActivatedPayload.class);

            if (payload.orderId() == null) {
                LOGGER.info("Ignoring subscription.activated.v1 without orderId (non-saga activation) "
                        + "messageId={}", messageId);
                return;
            }

            // Atomic inbox dedup: FulfillOrderCommand is an IdempotentRequest keyed on messageId;
            // InboxBehavior skips redelivery inside the handler transaction.
            mediator.send(new FulfillOrderCommand(
                    UUID.fromString(payload.orderId()), payload.subscriptionId(), messageId));

            LOGGER.info("subscription.activated.v1 processed messageId={} orderId={}",
                    messageId, payload.orderId());

        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.activated.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("subscription.activated.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
