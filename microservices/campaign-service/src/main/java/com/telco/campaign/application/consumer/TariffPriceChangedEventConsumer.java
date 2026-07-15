package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.FlagStaleTariffReferenceCommand;
import com.telco.campaign.application.dto.TariffPriceChangedPayload;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes {@code tariff.price-changed.v1} from product-catalog-service's {@code tariff.events} Kafka
 * topic defensively (Feature 21.4.3, ADR-027 Decision Section 4): never to mirror tariff pricing data,
 * only to flag every ACTIVE campaign whose {@code applicableTariffCodes} references the repriced
 * tariff, so a dangling/stale reference is caught here rather than surfacing only as a runtime
 * discrepancy at order time. Chosen behavior: an admin-visible flag/reason on {@code Campaign}
 * ({@code Campaign.flagStaleTariffReference}), NOT an auto-{@code expire()} - see
 * {@code docs/api-contracts/campaign-service.md} "Tariff-defensive behavior" for the rationale.
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code tariff.events} carries every product-catalog-service
 * tariff event type. This consumer discriminates on the canonical {@code eventType} Kafka header.
 *
 * <p><b>Idempotency:</b> {@link FlagStaleTariffReferenceCommand} is an {@code IdempotentRequest} keyed
 * on the Kafka record key; the platform {@code InboxBehavior} dedups redelivery. The handler itself is
 * also idempotent to redelivery: re-flagging an already-flagged campaign simply refreshes the
 * reason/timestamp, never double-applies any state-machine transition.
 *
 * <p><b>Distinct consumer group:</b> uses a dedicated {@code campaign-service-tariff-price-changed}
 * group id, never shared with {@link TariffCreatedEventConsumer} (which also reads
 * {@code tariff.events}).
 */
@Component
public class TariffPriceChangedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffPriceChangedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String TARIFF_PRICE_CHANGED_EVENT_TYPE = "tariff.price-changed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public TariffPriceChangedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "tariff.events", groupId = "campaign-service-tariff-price-changed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffPriceChanged(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!TARIFF_PRICE_CHANGED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring tariff event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            TariffPriceChangedPayload payload =
                    objectMapper.readValue(record.value(), TariffPriceChangedPayload.class);

            if (payload.code() == null) {
                LOGGER.warn("Ignoring tariff.price-changed.v1 without code messageId={}", messageId);
                return;
            }

            mediator.send(new FlagStaleTariffReferenceCommand(
                    payload.code(), "tariff.price-changed.v1 (tariffId=" + payload.tariffId() + ")",
                    messageId));

        } catch (Exception e) {
            LOGGER.error("Failed to process tariff.price-changed.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("tariff.price-changed.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
