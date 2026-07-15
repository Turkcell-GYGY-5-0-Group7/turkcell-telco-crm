package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.ReleaseRedemptionCommand;
import com.telco.campaign.application.dto.OrderCancelledPayload;
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
 * Consumes {@code order.cancelled.v1} from order-service's {@code order.events} Kafka topic and
 * releases the matching {@code CampaignRedemption} (RESERVED -&gt; RELEASED) by dispatching
 * {@link ReleaseRedemptionCommand} (Feature 21.4.2).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code order.events} carries every order-service event
 * type ({@code order.created.v1}, {@code order.cancelled.v1}, ...). This consumer discriminates on the
 * canonical {@code eventType} Kafka header the Debezium {@code EventRouter} populates from the outbox
 * {@code event_type} column, mirroring order-service's own consumer pattern. Only
 * {@code order.cancelled.v1} releases a redemption.
 *
 * <p><b>Idempotency (two layers):</b> (1) the dispatched {@link ReleaseRedemptionCommand} is an
 * {@code IdempotentRequest} keyed on the Kafka record key; the platform {@code InboxBehavior} dedups
 * redelivery of the same message ATOMICALLY inside the handler transaction; (2)
 * {@code ReleaseRedemptionCommandHandler} is check-then-act - it only releases a RESERVED redemption,
 * so an already-RELEASED redemption is a no-op even if the dedup key ever changed.
 *
 * <p><b>Distinct consumer group:</b> uses a dedicated {@code campaign-service-order-cancelled} group
 * id, never shared with {@link OrderCreatedRedemptionReservationConsumer} (which also reads
 * {@code order.events}) - two @KafkaListener members of the SAME consumer group compete for the
 * topic's partition(s) and would silently starve one another.
 */
@Component
public class OrderCancelledEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCancelledEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ORDER_CANCELLED_EVENT_TYPE = "order.cancelled.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public OrderCancelledEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "campaign-service-order-cancelled",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCancelled(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only order.cancelled.v1 releases a redemption.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ORDER_CANCELLED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring order event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            OrderCancelledPayload payload =
                    objectMapper.readValue(record.value(), OrderCancelledPayload.class);

            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring order.cancelled.v1 without orderId messageId={}", messageId);
                return;
            }

            mediator.send(new ReleaseRedemptionCommand(UUID.fromString(payload.orderId()), messageId));

            LOGGER.info("order.cancelled.v1 processed for redemption release messageId={} orderId={}",
                    messageId, payload.orderId());

        } catch (Exception e) {
            LOGGER.error("Failed to process order.cancelled.v1 for redemption release messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("order.cancelled.v1 redemption-release processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
