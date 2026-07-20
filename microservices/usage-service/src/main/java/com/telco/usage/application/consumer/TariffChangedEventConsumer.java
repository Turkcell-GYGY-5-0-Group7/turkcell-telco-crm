package com.telco.usage.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.usage.application.command.ReprovisionQuotaCommand;
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
 * Consumes {@code subscription.tariff-changed.v1} from the {@code subscription.events} topic and
 * re-provisions the subscription's current-period quota to the new tariff's allowances (Sprint 24
 * Feature 24.4, design-note D4) by dispatching {@link ReprovisionQuotaCommand} (ADR-008).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code subscription.events} carries several event
 * types; only the {@code eventType} header value {@code subscription.tariff-changed.v1} is
 * processed; anything else - or a missing header - is ignored.
 *
 * <p><b>Own consumer group:</b> the legacy {@code usage-service} group (activation provisioning)
 * also reads this topic; a dedicated groupId keeps the two listeners fan-out, not competing.
 *
 * <p><b>Idempotency:</b> the dispatched command is an {@code IdempotentRequest} keyed on the
 * BUSINESS key {@code orderId} - NOT the Kafka record key, which is the subscriptionId
 * (outbox aggregate_id) and therefore collides across successive plan changes of the same
 * subscription. InboxBehavior dedups atomically inside the handler transaction; a transient
 * handler failure (quota row not provisioned yet) rolls the inbox row back so redelivery retries.
 */
@Component
public class TariffChangedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffChangedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String TARIFF_CHANGED_EVENT_TYPE = "subscription.tariff-changed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public TariffChangedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "usage-service-tariff-changed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffChanged(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        // Type filter FIRST: fail closed on a missing or foreign eventType header.
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!TARIFF_CHANGED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring subscription event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            TariffChangedPayload payload =
                    objectMapper.readValue(record.value(), TariffChangedPayload.class);

            if (payload.subscriptionId() == null || payload.newTariffCode() == null
                    || payload.orderId() == null) {
                LOGGER.warn("Ignoring subscription.tariff-changed.v1 with missing mandatory fields "
                        + "messageId={}", messageId);
                return;
            }

            mediator.send(new ReprovisionQuotaCommand(
                    UUID.fromString(payload.subscriptionId()),
                    payload.newTariffCode(),
                    Instant.ofEpochMilli(payload.changedAt()),
                    payload.orderId()));

            LOGGER.info("subscription.tariff-changed.v1 processed messageId={} subscriptionId={} "
                    + "newTariff={}", messageId, payload.subscriptionId(), payload.newTariffCode());

        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.tariff-changed.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("subscription.tariff-changed.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Full mirror of the {@code subscription-tariff-changed.avsc} contract (guarded by
     * {@code UsageEventSchemaCompatTest}); this consumer reads the subscription, new tariff,
     * order and timing fields. Unknown future fields are ignored (ADR-019).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TariffChangedPayload(
            String subscriptionId,
            String customerId,
            String msisdn,
            String previousTariffCode,
            String newTariffCode,
            int newTariffVersion,
            String orderId,
            long changedAt
    ) {
    }
}
