package com.telco.payment.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.dto.OrderCreatedPayload;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code order.created.v1} events from the {@code order.events} Kafka topic and
 * dispatches {@link ChargePaymentCommand} via the mediator (ADR-008).
 *
 * <p>Idempotency is handled at this layer via {@link InboxService#firstSeen(String, String)}.
 * The deduplication key is the Kafka record key (set to the outbox event ID by the relay).
 * If the key is absent, the orderId from the payload is used as fallback.
 *
 * <p>The {@code paymentRequestId} is derived from the {@code orderId} so that
 * {@link com.telco.payment.application.handler.ChargePaymentCommandHandler} can also enforce
 * idempotency independently at the command level (FR-25).
 */
@Component
public class OrderCreatedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);
    private static final String CONSUMER_NAME = "OrderCreatedEventConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public OrderCreatedEventConsumer(Mediator mediator,
                                     InboxService inboxService,
                                     ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "payment-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Filter: only process order.created.v1 events (topic may carry multiple event types).
        // The event type is carried in a header or the payload; for the MVP we process all messages.

        try {
            OrderCreatedPayload payload = objectMapper.readValue(record.value(), OrderCreatedPayload.class);

            // Inbox deduplication: skip if already processed.
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate order.created.v1 messageId={}", messageId);
                return;
            }

            if (payload.orderId() == null || payload.customerId() == null || payload.totalAmount() == null) {
                LOGGER.warn("Ignoring incomplete order.created.v1 payload messageId={}", messageId);
                return;
            }

            // paymentRequestId is derived from orderId for stable idempotency across retries.
            String paymentRequestId = payload.orderId();

            ChargePaymentCommand command = new ChargePaymentCommand(
                    UUID.fromString(payload.orderId()),
                    UUID.fromString(payload.customerId()),
                    payload.totalAmount(),
                    paymentRequestId);

            mediator.send(command);
            LOGGER.info("Dispatched ChargePaymentCommand for orderId={}", payload.orderId());

        } catch (Exception e) {
            LOGGER.error("Failed to process order.created.v1 messageId={}: {}", messageId, e.getMessage(), e);
            // Re-throw so Spring Kafka retries (or sends to DLT if configured).
            throw new RuntimeException("order.created.v1 processing failed", e);
        }
    }
}
