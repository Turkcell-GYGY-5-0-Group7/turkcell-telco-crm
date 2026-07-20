package com.telco.subscription.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.AttachAddonCommand;
import com.telco.subscription.application.command.ChangeSubscriptionTariffCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Provisions PLAN_CHANGE and ADDON orders from {@code order.created.v1} (FR-09). These order types
 * skip the payment leg (payment-service ignores them; the fee bills on the next invoice, FR-22), so
 * order creation itself is the provisioning trigger - unlike NEW_LINE, which activates on
 * {@code payment.completed.v1} in {@link PaymentCompletedEventConsumer}.
 *
 * <p>NEW_LINE (or pre-FR-09 null-type) events are ignored here; the event carries every needed
 * snapshot (tariffCode / addonCode / addonType / unitPrice / currency), so no synchronous order
 * lookup is needed. Distinct consumer group; idempotency via the commands' {@code IdempotentRequest}
 * inbox keys (the Kafka record key), with the {@code (orderId, addonCode)} unique index as the
 * addon path's second line of defense.
 */
@Component
public class OrderCreatedProvisioningConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreatedProvisioningConsumer.class);
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String ORDER_CREATED_EVENT_TYPE = "order.created.v1";

    private final Mediator mediator;
    private final ObjectMapper objectMapper;

    public OrderCreatedProvisioningConsumer(Mediator mediator, ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "subscription-service-order-provisioning",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-" + record.offset();

        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!ORDER_CREATED_EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring order event of type={} messageId={}", eventType, messageId);
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            String orderType = payload.orderType();
            if (orderType == null || "NEW_LINE".equals(orderType)) {
                // NEW_LINE provisioning happens on payment.completed.v1, not here.
                return;
            }
            if (payload.orderId() == null || payload.customerId() == null
                    || payload.subscriptionId() == null
                    || payload.items() == null || payload.items().size() != 1) {
                LOGGER.warn("Ignoring malformed {} order.created.v1 messageId={}", orderType, messageId);
                return;
            }
            Item item = payload.items().get(0);
            UUID orderId = UUID.fromString(payload.orderId());
            UUID customerId = UUID.fromString(payload.customerId());
            UUID subscriptionId = UUID.fromString(payload.subscriptionId());

            switch (orderType) {
                case "PLAN_CHANGE" -> {
                    if (item.tariffCode() == null) {
                        LOGGER.warn("PLAN_CHANGE order without tariffCode messageId={}", messageId);
                        return;
                    }
                    mediator.send(new ChangeSubscriptionTariffCommand(
                            subscriptionId, orderId, customerId, item.tariffCode(), messageId));
                }
                case "ADDON" -> {
                    if (item.addonCode() == null || item.unitPrice() == null) {
                        LOGGER.warn("ADDON order without addonCode/unitPrice messageId={}", messageId);
                        return;
                    }
                    mediator.send(new AttachAddonCommand(
                            subscriptionId, orderId, customerId, item.addonCode(), item.addonType(),
                            item.unitPrice(), item.currency() == null ? "TRY" : item.currency(),
                            messageId));
                }
                default -> LOGGER.warn("Unknown orderType={} messageId={}", orderType, messageId);
            }
            LOGGER.info("order.created.v1 ({}) processed messageId={} orderId={}",
                    orderType, messageId, payload.orderId());
        } catch (Exception e) {
            LOGGER.error("Failed to process order.created.v1 messageId={}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("order.created.v1 provisioning failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Minimal JSON shape of order.created.v1; unknown fields ignored (ADR-019). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String orderId, String customerId, String orderType, String subscriptionId,
                   List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String tariffCode, String addonCode, String addonType,
                BigDecimal unitPrice, String currency) {
    }
}
