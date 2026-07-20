package com.telco.order.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.FulfillOrderCommand;
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
 * Consumes {@code subscription.tariff-changed.v1} from {@code subscription.events} and fulfills
 * the PLAN_CHANGE order that drove the change (Sprint 24 Feature 24.4, design-note D2) by
 * dispatching {@link FulfillOrderCommand} - the same terminal saga step
 * {@code subscription.activated.v1} drives for NEW_LINE orders, including the PENDING-rethrow
 * transient-race handling.
 *
 * <p><b>Type filter FIRST (fail closed):</b> only the {@code eventType} header value
 * {@code subscription.tariff-changed.v1} fulfills; any other type - or a missing header - is
 * ignored. This listener has its own group id ({@code order-service} already reads this topic for
 * activations - fan-out, not competing consumption).
 *
 * <p><b>Idempotency:</b> the dispatched {@link FulfillOrderCommand} is keyed on the BUSINESS key
 * {@code "plan-change-fulfill:" + orderId}, NOT the Kafka record key: tariff-changed events are
 * keyed by subscriptionId (outbox aggregate_id), so a second plan change of the same subscription
 * would collide with the first and its fulfillment would be swallowed as a duplicate. The
 * handler's check-then-act (only CONFIRMED fulfills) is the second safety layer.
 */
@Component
public class SubscriptionTariffChangedEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionTariffChangedEventConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String TARIFF_CHANGED_EVENT_TYPE = "subscription.tariff-changed.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public SubscriptionTariffChangedEventConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "order-service-tariff-changed",
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

            if (payload.orderId() == null) {
                LOGGER.warn("Ignoring subscription.tariff-changed.v1 without orderId messageId={}",
                        messageId);
                return;
            }

            mediator.send(new FulfillOrderCommand(
                    UUID.fromString(payload.orderId()),
                    payload.subscriptionId(),
                    "plan-change-fulfill:" + payload.orderId()));

            LOGGER.info("subscription.tariff-changed.v1 processed messageId={} orderId={}",
                    messageId, payload.orderId());

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
     * {@code OrderEventSchemaCompatTest}); this consumer reads only orderId and subscriptionId.
     * Unknown future fields are ignored (ADR-019).
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
