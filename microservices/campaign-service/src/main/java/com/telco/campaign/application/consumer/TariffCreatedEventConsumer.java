package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.LogStaleTariffReferenceCommand;
import com.telco.campaign.application.dto.TariffCreatedPayload;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes {@code tariff.created.v1} from product-catalog-service's {@code tariff.events} Kafka topic
 * defensively (Feature 21.4.3, ADR-027 Decision Section 4): never to mirror tariff pricing data, only
 * to log a diagnostic when the (re)created tariff code is referenced by an ACTIVE campaign - a
 * possible sign of unintended tariff-code reuse. See {@link LogStaleTariffReferenceCommand}'s Javadoc
 * for why this path is log-only (not the admin-visible flag {@code tariff.price-changed.v1} sets).
 *
 * <p><b>Type filter FIRST (fail closed):</b> {@code tariff.events} carries every product-catalog-service
 * tariff event type. This consumer discriminates on the canonical {@code eventType} Kafka header.
 *
 * <p><b>Idempotency:</b> {@link LogStaleTariffReferenceCommand} is an {@code IdempotentRequest} keyed
 * on the Kafka record key; the platform {@code InboxBehavior} dedups redelivery.
 *
 * <p><b>Distinct consumer group:</b> uses a dedicated {@code campaign-service-tariff-created} group id,
 * never shared with {@link TariffPriceChangedEventConsumer} (which also reads {@code tariff.events}).
 */
@Component
public class TariffCreatedEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffCreatedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String TARIFF_CREATED_EVENT_TYPE = "tariff.created.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public TariffCreatedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "tariff.events", groupId = "campaign-service-tariff-created",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffCreated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!TARIFF_CREATED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring tariff event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            TariffCreatedPayload payload =
                    objectMapper.readValue(record.value(), TariffCreatedPayload.class);

            if (payload.code() == null) {
                LOGGER.warn("Ignoring tariff.created.v1 without code messageId={}", messageId);
                return;
            }

            mediator.send(new LogStaleTariffReferenceCommand(payload.code(), messageId));

        } catch (Exception e) {
            LOGGER.error("Failed to process tariff.created.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("tariff.created.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
