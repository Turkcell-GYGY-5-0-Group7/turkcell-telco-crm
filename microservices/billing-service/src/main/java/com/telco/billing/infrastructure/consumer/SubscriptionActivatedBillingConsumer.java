package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordSubscriptionActivatedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Distinct consumer group (bug found via live acceptance testing, 2026-07-06): this listener,
 * {@link SubscriptionTerminatedBillingConsumer}, and {@link SubscriptionSuspendedBillingConsumer}
 * all read {@code subscription.events} and previously shared {@code groupId="billing-service"}.
 * Three @KafkaListener members of the SAME consumer group compete for the topic's partition(s):
 * Kafka's group coordinator hands the (single, dev-sized) partition to exactly one member,
 * permanently starving the other two of every message on the topic - so at most one of
 * activation/termination/suspension billing side-effects could ever fire in a given session. Each
 * now gets its own dedicated group id so all three independently see every message (fan-out, not
 * competing consumption) and filter internally by {@code eventType} as designed.
 */
@Component
public class SubscriptionActivatedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionActivatedBillingConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionActivatedBillingConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionActivatedBillingConsumer(Mediator mediator, InboxService inboxService,
                                                ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "billing-service-subscription-activated",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();
        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.subscriptionId() == null || payload.customerId() == null) {
                return;
            }

            // Filter: only handle activation events (eventType header or check fields).
            // The subscription.events topic carries multiple event types; skip non-activated ones.
            if (payload.tariffCode() == null) {
                return;
            }

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate subscription.activated.v1 messageId={}", messageId);
                return;
            }

            // activatedAt is published as epoch millis (subscription-service SubscriptionActivatedV1,
            // a long, matching the Avro timestamp-millis contract), not an ISO-8601 string. Using
            // Instant.parse(...) here (bug found via live acceptance testing, 2026-07-06) threw
            // DateTimeParseException on every real message, and since the inbox row is marked seen
            // before this line runs, every Kafka redelivery retry after the crash was then silently
            // swallowed as a "duplicate" - billing never learned about a single activated
            // subscription in this environment, so a bill-run never had anything to invoice.
            Instant activatedAt = payload.activatedAt() != null
                    ? Instant.ofEpochMilli(payload.activatedAt()) : Instant.now();

            mediator.send(new RecordSubscriptionActivatedCommand(
                    UUID.fromString(payload.subscriptionId()),
                    UUID.fromString(payload.customerId()),
                    payload.tariffCode(),
                    activatedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.activated.v1 messageId={}", messageId, e);
            throw new RuntimeException("subscription.activated.v1 billing consumer failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String subscriptionId, String customerId,
                          String tariffCode, Long activatedAt) {}
}
