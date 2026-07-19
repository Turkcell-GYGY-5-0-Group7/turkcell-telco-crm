package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordAddonPurchaseCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code addon.purchased.v1} from the {@code addon.events} topic and records an unbilled
 * addon charge for the next bill run (Sprint 24 Feature 24.3, design-note D3, FR-22).
 *
 * <p><b>Type filter FIRST (fail closed):</b> discriminates on the canonical {@code eventType}
 * Kafka header the Debezium EventRouter populates; any other type - or a missing header - is
 * ignored, even though {@code addon.events} currently carries a single type.
 *
 * <p><b>Own consumer group:</b> usage-service also reads {@code addon.events}; each listener has a
 * dedicated groupId so both independently see every message (fan-out, not competing consumption -
 * same reasoning as the subscription.events consumers in this package).
 *
 * <p><b>Idempotency:</b> unlike the older consumers in this package (manual
 * {@code inboxService.firstSeen} BEFORE dispatch - a pattern retired after it swallowed
 * redeliveries of crashed handlers), dedup is delegated to the mediator: the dispatched
 * {@link RecordAddonPurchaseCommand} is an {@code IdempotentRequest} keyed on the record key (the
 * order-item id), and the platform {@code InboxBehavior} dedups atomically INSIDE the handler
 * transaction.
 *
 * <p>The charge amount is {@code price * quantity}: the event's {@code price} is per unit, and the
 * bill-run invoices one line per purchase row (not per unit). A null {@code currency} (pre-V9
 * order rows) falls back to TRY per the contract doc.
 */
@Component
public class AddonPurchasedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonPurchasedBillingConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ADDON_PURCHASED_EVENT_TYPE = "addon.purchased.v1";
    private static final String FALLBACK_CURRENCY = "TRY";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public AddonPurchasedBillingConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "addon.events", groupId = "billing-service-addon-purchased",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onAddonPurchased(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();

        // Type filter FIRST: fail closed on a missing or foreign eventType header.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ADDON_PURCHASED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring addon event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            AddonPurchasedPayload payload =
                    objectMapper.readValue(record.value(), AddonPurchasedPayload.class);

            if (payload.subscriptionId() == null || payload.customerId() == null
                    || payload.addonCode() == null || payload.price() == null) {
                LOGGER.warn("Ignoring addon.purchased.v1 with missing mandatory fields messageId={}",
                        messageId);
                return;
            }

            BigDecimal chargeAmount = payload.price()
                    .multiply(BigDecimal.valueOf(Math.max(payload.quantity(), 1)));
            Instant purchasedAt = payload.occurredAt() != null
                    ? Instant.parse(payload.occurredAt()) : Instant.now();

            mediator.send(new RecordAddonPurchaseCommand(
                    UUID.fromString(payload.subscriptionId()),
                    UUID.fromString(payload.customerId()),
                    payload.addonCode(),
                    payload.addonName(),
                    chargeAmount,
                    payload.currency() != null ? payload.currency() : FALLBACK_CURRENCY,
                    purchasedAt,
                    messageId));

            LOGGER.info("addon.purchased.v1 recorded for billing messageId={} subscriptionId={} addon={}",
                    messageId, payload.subscriptionId(), payload.addonCode());

        } catch (Exception e) {
            LOGGER.error("Failed to process addon.purchased.v1 messageId={}", messageId, e);
            throw new RuntimeException("addon.purchased.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Full mirror of the {@code addon-purchased.avsc} contract (guarded by
     * {@code BillingEventSchemaCompatTest}); this consumer only reads the identity, price and
     * timing fields. Unknown future fields are ignored for forward-compatibility (ADR-019).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddonPurchasedPayload(
            String orderId,
            String customerId,
            String subscriptionId,
            String addonCode,
            String addonName,
            String addonType,
            BigDecimal price,
            String currency,
            int quantity,
            Long allowanceDataMb,
            Long allowanceMinutes,
            Long allowanceSms,
            String occurredAt
    ) {
    }
}
