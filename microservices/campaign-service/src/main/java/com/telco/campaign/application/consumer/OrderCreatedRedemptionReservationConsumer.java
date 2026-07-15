package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.CreateRedemptionReservationCommand;
import com.telco.campaign.application.dto.OrderCreatedPayload;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code order.created.v1} from order-service's {@code order.events} Kafka topic and creates
 * one {@code RESERVED} {@code CampaignRedemption} row per campaign-priced line item, by dispatching
 * {@link CreateRedemptionReservationCommand} (Feature 21.4.3, ADR-027 Decision Section 4, resolving the
 * reservation-timing open item from 21.2.2). An order with no campaign-priced items (every item's
 * {@code campaignId} is {@code null}) is a silent no-op.
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code order.events} carries every order-service event
 * type. This consumer discriminates on the canonical {@code eventType} Kafka header, mirroring
 * order-service's own consumer pattern. Only {@code order.created.v1} creates a reservation.
 *
 * <p><b>Idempotency:</b> each item's {@link CreateRedemptionReservationCommand} is an
 * {@code IdempotentRequest} keyed on {@code messageId + ":" + campaignId} (not {@code messageId} alone
 * - a single order can carry multiple items priced against different campaigns, each needing its own
 * dedup identity within the same Kafka record); the platform {@code InboxBehavior} dedups redelivery
 * of each item's command independently, atomically inside its own handler transaction. Concurrent
 * duplicate {@code order.created.v1} events for the SAME campaign at the cap boundary are additionally
 * serialized by {@code CampaignEligibilityService.reserve}'s {@code PESSIMISTIC_WRITE} lock on the
 * {@code Campaign} row (Feature 21.2.2), so cap-safety holds even across concurrent messages, not just
 * redelivery of one message.
 *
 * <p><b>Distinct consumer group:</b> uses a dedicated {@code campaign-service-redemption-reservation}
 * group id, never shared with {@link OrderCancelledEventConsumer} (which also reads
 * {@code order.events}) - two @KafkaListener members of the SAME consumer group compete for the
 * topic's partition(s) and would silently starve one another.
 */
@Component
public class OrderCreatedRedemptionReservationConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OrderCreatedRedemptionReservationConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ORDER_CREATED_EVENT_TYPE = "order.created.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public OrderCreatedRedemptionReservationConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "campaign-service-redemption-reservation",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: only order.created.v1 creates a reservation.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ORDER_CREATED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring order event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            OrderCreatedPayload payload =
                    objectMapper.readValue(record.value(), OrderCreatedPayload.class);

            if (payload.orderId() == null || payload.customerId() == null) {
                LOGGER.warn("Ignoring order.created.v1 without orderId/customerId messageId={}", messageId);
                return;
            }

            UUID orderId = UUID.fromString(payload.orderId());
            UUID customerId = UUID.fromString(payload.customerId());
            List<OrderCreatedPayload.OrderItemPayload> items =
                    payload.items() == null ? List.of() : payload.items();

            int reserved = 0;
            for (OrderCreatedPayload.OrderItemPayload item : items) {
                if (item.campaignId() == null) {
                    continue;
                }
                UUID campaignId = UUID.fromString(item.campaignId());
                String itemIdempotencyKey = messageId + ":" + campaignId;
                mediator.send(new CreateRedemptionReservationCommand(
                        campaignId, customerId, orderId, itemIdempotencyKey));
                reserved++;
            }

            LOGGER.info("order.created.v1 processed messageId={} orderId={} campaignItems={}",
                    messageId, payload.orderId(), reserved);

        } catch (Exception e) {
            LOGGER.error("Failed to process order.created.v1 for redemption reservation messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("order.created.v1 redemption-reservation processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
