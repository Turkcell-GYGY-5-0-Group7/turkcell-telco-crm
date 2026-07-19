package com.telco.usage.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.usage.application.command.TopUpQuotaCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code addon.purchased.v1} from the {@code addon.events} Kafka topic and tops up the
 * target subscription's quota with the purchased allowance deltas (Sprint 24 Feature 24.3,
 * design-note D4) by dispatching {@link TopUpQuotaCommand} (ADR-008).
 *
 * <p><b>Type filter FIRST (fail closed):</b> the consumer discriminates on the canonical
 * {@code eventType} Kafka header the Debezium EventRouter populates; a message with another type -
 * or no header - is ignored, even though {@code addon.events} currently carries a single type.
 *
 * <p><b>Own consumer group:</b> billing-service also reads {@code addon.events}; each listener has
 * a dedicated groupId so both independently see every message (fan-out, not competing consumption).
 *
 * <p><b>Idempotency:</b> the dispatched {@link TopUpQuotaCommand} is an {@code IdempotentRequest}
 * keyed on the Kafka record key (the producer's outbox aggregate_id = the order-item id); the
 * platform {@code InboxBehavior} dedups redelivery atomically INSIDE the handler transaction. The
 * consumer writes no inbox row itself: a transient handler failure (quota not provisioned yet)
 * rolls everything back and redelivery retries.
 *
 * <p>Allowance deltas in the event are per unit; the consumer multiplies by {@code quantity}
 * before dispatch (contract doc on {@code addon-purchased.avsc}).
 */
@Component
public class AddonPurchasedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonPurchasedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ADDON_PURCHASED_EVENT_TYPE = "addon.purchased.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public AddonPurchasedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "addon.events", groupId = "usage-service-addon-purchased",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onAddonPurchased(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: fail closed on a missing or foreign eventType header.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ADDON_PURCHASED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring addon event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            AddonPurchasedPayload payload =
                    objectMapper.readValue(record.value(), AddonPurchasedPayload.class);

            if (payload.subscriptionId() == null) {
                LOGGER.warn("Ignoring addon.purchased.v1 without subscriptionId messageId={}", messageId);
                return;
            }

            long minutes = delta(payload.allowanceMinutes(), payload.quantity());
            long sms = delta(payload.allowanceSms(), payload.quantity());
            long mb = delta(payload.allowanceDataMb(), payload.quantity());

            if (minutes == 0 && sms == 0 && mb == 0) {
                // e.g. a VAS addon with no allowance component: nothing to top up. Not an error.
                LOGGER.info("addon.purchased.v1 with no allowance deltas (addon {}); nothing to "
                        + "top up messageId={}", payload.addonCode(), messageId);
                return;
            }

            Instant occurredAt = payload.occurredAt() != null
                    ? Instant.parse(payload.occurredAt()) : Instant.now();

            mediator.send(new TopUpQuotaCommand(
                    UUID.fromString(payload.subscriptionId()), minutes, sms, mb,
                    occurredAt, messageId));

            LOGGER.info("addon.purchased.v1 processed messageId={} subscriptionId={} addon={}",
                    messageId, payload.subscriptionId(), payload.addonCode());

        } catch (Exception e) {
            LOGGER.error("Failed to process addon.purchased.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("addon.purchased.v1 processing failed", e);
        }
    }

    private static long delta(Long perUnit, int quantity) {
        return perUnit == null ? 0L : perUnit * quantity;
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Full mirror of the {@code addon-purchased.avsc} contract (guarded by
     * {@code UsageEventSchemaCompatTest}); this consumer only reads the subscription, quantity and
     * allowance fields. Unknown future fields are ignored for forward-compatibility (ADR-019).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AddonPurchasedPayload(
            String orderId,
            String customerId,
            String subscriptionId,
            String addonCode,
            String addonName,
            String addonType,
            java.math.BigDecimal price,
            String currency,
            int quantity,
            Long allowanceDataMb,
            Long allowanceMinutes,
            Long allowanceSms,
            String occurredAt
    ) {
    }
}
