package com.telco.subscription.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.command.FailSubscriptionActivationCommand;
import com.telco.subscription.application.dto.PaymentCompletedPayload;
import com.telco.subscription.infrastructure.client.OrderClientResponse;
import com.telco.subscription.infrastructure.client.OrderItemClientResponse;
import com.telco.subscription.infrastructure.client.OrderLookupRejectedException;
import com.telco.subscription.infrastructure.client.OrderServiceClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumes {@code payment.completed.v1} from the {@code payment.events} Kafka topic and activates the
 * order's subscription (saga step 4, FR-13, AC-01). This is the producer of {@code subscription.activated.v1}.
 *
 * <p>Activation needs the tariff snapshot ({@code tariffCode}/{@code tariffVersion}) and the
 * authoritative {@code customerId}, neither of which the payment event carries. The consumer makes
 * exactly ONE synchronous hop to order-service ({@link OrderServiceClient#getOrder}) for them
 * (architecture Option (b)).
 *
 * <p>Event-type filtering: {@code payment.events} carries every payment event type, so the consumer
 * acts only when the {@code eventType} header equals {@code payment.completed.v1} (fails closed on a
 * missing/other type). It never decides on payload shape.
 *
 * <p>Failure modes are deliberately split so a transient outage never compensates:
 * <ul>
 *   <li>TRANSIENT order lookup failure ({@link DependencyFailureException}: order-service down,
 *       circuit open, 5xx, timeout) -&gt; re-throw so the Kafka listener retries. No command is
 *       dispatched, so no inbox row is written and redelivery re-attempts the hop.</li>
 *   <li>TERMINAL order missing ({@link ResourceNotFoundException}: order genuinely missing
 *       post-payment) -&gt; dispatch {@code FailSubscriptionActivationCommand} (reason
 *       {@code ORDER_NOT_FOUND}) so the saga compensates.</li>
 *   <li>TERMINAL order lookup rejected ({@link OrderLookupRejectedException}: non-404 4xx, a
 *       contract/auth defect that cannot heal by redelivery) -&gt; dispatch
 *       {@code FailSubscriptionActivationCommand} (reason {@code ORDER_LOOKUP_REJECTED}).</li>
 *   <li>Multi-item order (one-line MVP invariant violated) -&gt; dispatch
 *       {@code FailSubscriptionActivationCommand} (reason {@code UNSUPPORTED_MULTI_ITEM_ORDER}).</li>
 * </ul>
 *
 * <p>Idempotency: dedup is delegated to the mediator. The dispatched commands
 * ({@code ActivateSubscriptionCommand}, {@code FailSubscriptionActivationCommand}) are
 * {@code IdempotentRequest}s, so {@code InboxBehavior} dedups INSIDE the handler transaction (ADR-005).
 * The consumer therefore writes NO inbox row itself; crucially the sync order-service hop happens
 * BEFORE any command is sent, so a transient {@link DependencyFailureException} propagates with no
 * inbox write and redelivery retries the hop.
 *
 * <p><b>Distinct consumer group (bug found via live acceptance testing, 2026-07-06):</b> this
 * listener and {@link PaymentFailedEventConsumer} both read {@code payment.events}, and both
 * previously used the shared {@code groupId="subscription-service"}. Two @KafkaListener members of
 * the SAME consumer group compete for the topic's partition(s): Kafka's group coordinator hands the
 * (single, dev-sized) partition to exactly one member, permanently starving the other of every
 * message on the topic. Each now gets its own dedicated group id so both independently see every
 * message (fan-out, not competing consumption) and filter internally by {@code eventType} as
 * designed.
 */
@Component
public class PaymentCompletedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCompletedEventConsumer.class);
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String PAYMENT_COMPLETED_EVENT_TYPE = "payment.completed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;
    private final OrderServiceClient orderServiceClient;

    public PaymentCompletedEventConsumer(Mediator mediator,
                                         ObjectMapper objectMapper,
                                         OrderServiceClient orderServiceClient) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
        this.orderServiceClient = orderServiceClient;
    }

    @KafkaListener(topics = "payment.events", groupId = "subscription-service-payment-completed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCompleted(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only payment.completed.v1 activates. Other payment events on this topic
        // (failed/refunded) are ignored here.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!PAYMENT_COMPLETED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring payment event of type={} messageId={}", eventType, messageId);
            return;
        }

        PaymentCompletedPayload payload;
        try {
            payload = objectMapper.readValue(record.value(), PaymentCompletedPayload.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse payment.completed.v1 messageId={}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("payment.completed.v1 parse failed", e);
        }

        if (payload.orderId() == null || payload.customerId() == null) {
            LOGGER.warn("Ignoring payment.completed.v1 missing orderId/customerId messageId={}", messageId);
            return;
        }
        UUID orderId = UUID.fromString(payload.orderId());
        UUID payloadCustomerId = UUID.fromString(payload.customerId());

        // SYNC HOP FIRST (before any mediator.send): a TRANSIENT DependencyFailureException must
        // propagate so the Kafka listener retries. No command is dispatched on that path, so no inbox
        // row is written and redelivery re-attempts the hop. Dedup now happens at mediator.send time
        // (InboxBehavior, inside the handler tx), strictly AFTER this hop.
        OrderClientResponse order;
        try {
            order = orderServiceClient.getOrder(orderId);
        } catch (ResourceNotFoundException e) {
            // TERMINAL: order missing post-payment -> compensate.
            mediator.send(new FailSubscriptionActivationCommand(
                    orderId, payloadCustomerId, "ORDER_NOT_FOUND", messageId));
            LOGGER.warn("Order {} not found for payment.completed.v1; emitted activation-failed", orderId);
            return;
        } catch (OrderLookupRejectedException e) {
            // TERMINAL: non-404 4xx (contract/auth defect) -> fail closed to compensation, never retry.
            mediator.send(new FailSubscriptionActivationCommand(
                    orderId, payloadCustomerId, "ORDER_LOOKUP_REJECTED", messageId));
            LOGGER.warn("Order {} lookup rejected for payment.completed.v1; emitted activation-failed", orderId);
            return;
        }
        // DependencyFailureException (transient) propagates uncaught -> Kafka retry, no inbox write.

        UUID customerId = order.customerId() != null ? order.customerId() : payloadCustomerId;

        if (order.items() == null || order.items().size() != 1) {
            int count = order.items() == null ? 0 : order.items().size();
            LOGGER.warn("Order {} has {} items; one-line MVP invariant violated -> activation-failed", orderId, count);
            mediator.send(new FailSubscriptionActivationCommand(
                    orderId, customerId, "UNSUPPORTED_MULTI_ITEM_ORDER", messageId));
            return;
        }

        OrderItemClientResponse item = order.items().get(0);
        mediator.send(new ActivateSubscriptionCommand(orderId, customerId, item.tariffCode(), item.tariffVersion()));
        LOGGER.info("Activated subscription for orderId={} customerId={}", orderId, customerId);
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
