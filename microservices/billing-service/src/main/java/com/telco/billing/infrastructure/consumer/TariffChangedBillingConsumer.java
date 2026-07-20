package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordTariffChangedCommand;
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
 * Consumes {@code subscription.tariff-changed.v1} from {@code subscription.events} and updates the
 * subscriber's billing record so the next bill run uses the new tariff's fee (Sprint 24 Feature
 * 24.4, design-note D2).
 *
 * <p><b>Type filter FIRST (fail closed):</b> discriminates on the {@code eventType} header; any
 * other type - or a missing header - is ignored.
 *
 * <p><b>Own consumer group:</b> the other subscription.events billing listeners each have their
 * own dedicated group id (fan-out, not competing consumption); this one follows suit.
 *
 * <p><b>Idempotency:</b> like {@link AddonPurchasedBillingConsumer} (and unlike the older manual
 * {@code firstSeen} consumers in this package), dedup is delegated to the mediator: the dispatched
 * {@link RecordTariffChangedCommand} is an {@code IdempotentRequest} keyed on the BUSINESS key
 * {@code orderId} - the Kafka record key is the subscriptionId (outbox aggregate_id) and would
 * collide across successive plan changes of the same subscription.
 */
@Component
public class TariffChangedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffChangedBillingConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String TARIFF_CHANGED_EVENT_TYPE = "subscription.tariff-changed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public TariffChangedBillingConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "billing-service-tariff-changed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffChanged(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();

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

            mediator.send(new RecordTariffChangedCommand(
                    UUID.fromString(payload.subscriptionId()),
                    payload.newTariffCode(),
                    payload.orderId()));

            LOGGER.info("subscription.tariff-changed.v1 recorded for billing messageId={} "
                    + "subscriptionId={} newTariff={}", messageId, payload.subscriptionId(),
                    payload.newTariffCode());

        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.tariff-changed.v1 messageId={}", messageId, e);
            throw new RuntimeException("subscription.tariff-changed.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Full mirror of the {@code subscription-tariff-changed.avsc} contract (guarded by
     * {@code BillingEventSchemaCompatTest}); this consumer reads the subscription, new tariff and
     * order fields. Unknown future fields are ignored (ADR-019).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TariffChangedPayload(
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
