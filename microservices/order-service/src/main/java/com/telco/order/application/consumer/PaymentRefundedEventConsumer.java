package com.telco.order.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.CompensateOrderCommand;
import com.telco.order.application.dto.PaymentRefundedPayload;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderStatus;
import com.telco.order.infrastructure.persistence.OrderRepository;
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
 * Consumes {@code payment.refunded.v1} from the {@code payment.events} Kafka topic and compensates
 * the saga by cancelling the order (saga step COMPENSATED / status CANCELLED) (ADR-008, AC-01).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code payment.events} carries every payment event
 * type. The consumer discriminates on the canonical {@code eventType} Kafka header; only
 * {@code payment.refunded.v1} compensates. Any other type - or a message with no {@code eventType}
 * header - is ignored.
 *
 * <p><b>System actor:</b> the order is cancelled via the saga-only {@link CompensateOrderCommand}
 * (no caller identity, bypasses the customer ownership guard) with
 * {@code reason="SAGA_COMPENSATION"}. {@code Order.cancel()} allows PENDING or CONFIRMED -> CANCELLED.
 *
 * <p><b>Idempotency (atomic inbox + check-then-act):</b> {@link CompensateOrderCommand} is an
 * {@code IdempotentRequest} keyed on the Kafka record key, so the platform {@code InboxBehavior}
 * dedups redelivery of the same message ATOMICALLY inside the handler transaction (tech-lead ruling
 * 2a/2b). The handler is also check-then-act (a fresh messageId on an already-CANCELLED/terminal
 * order is a no-op). The consumer additionally read-checks the status FIRST as defense-in-depth, so
 * an already-resolved order skips dispatch entirely.
 *
 * <p><b>Distinct consumer group (bug found via live acceptance testing, 2026-07-06):</b> this
 * listener previously shared {@code groupId="order-service"} with
 * {@link PaymentCompletedEventConsumer} on the same {@code payment.events} topic. Two
 * @KafkaListener members of the SAME consumer group compete for the topic's partition(s): Kafka's
 * group coordinator handed the (single, dev-sized) partition to that other consumer and starved
 * this one of every message on the topic for the entire session - AC-01's compensation path never
 * advanced past order status CONFIRMED because this listener never fired at all. Now has its own
 * dedicated group id so both consumers independently see every message (fan-out, not competing
 * consumption) and filter internally by {@code eventType} as designed.
 */
@Component
public class PaymentRefundedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRefundedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String PAYMENT_REFUNDED_EVENT_TYPE = "payment.refunded.v1";
    private static final String COMPENSATION_REASON = "SAGA_COMPENSATION";

    private final Mediator mediator;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public PaymentRefundedEventConsumer(Mediator mediator,
                                        OrderRepository orderRepository,
                                        ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.events", groupId = "order-service-payment-refunded",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentRefunded(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only payment.refunded.v1 compensates.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!PAYMENT_REFUNDED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring payment event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            PaymentRefundedPayload payload =
                    objectMapper.readValue(record.value(), PaymentRefundedPayload.class);

            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring payment.refunded.v1 without orderId messageId={}", messageId);
                return;
            }

            UUID orderId = UUID.fromString(payload.orderId());

            // Defense-in-depth check-then-act: skip if the order is no longer cancellable (already
            // CANCELLED, or terminal FULFILLED/FAILED). The handler guards this too; the consumer
            // pre-read avoids an unnecessary command dispatch.
            Optional<Order> order = orderRepository.findById(orderId);
            if (order.isEmpty()) {
                LOGGER.warn("payment.refunded.v1 for unknown orderId={} messageId={}", orderId, messageId);
                return;
            }
            OrderStatus status = order.get().getStatus();
            if (status != OrderStatus.PENDING && status != OrderStatus.CONFIRMED) {
                LOGGER.info("Skipping compensation: order {} already in status {} messageId={}",
                        orderId, status, messageId);
                return;
            }

            // Atomic inbox dedup: CompensateOrderCommand is an IdempotentRequest keyed on messageId;
            // InboxBehavior skips redelivery inside the handler transaction.
            mediator.send(new CompensateOrderCommand(orderId, COMPENSATION_REASON, messageId));

            LOGGER.info("payment.refunded.v1 compensated messageId={} orderId={}", messageId, orderId);

        } catch (Exception e) {
            LOGGER.error("Failed to process payment.refunded.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("payment.refunded.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
