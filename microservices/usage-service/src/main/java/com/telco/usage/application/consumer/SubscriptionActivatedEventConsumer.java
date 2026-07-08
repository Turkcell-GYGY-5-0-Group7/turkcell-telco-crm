package com.telco.usage.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.usage.application.command.ProvisionQuotaCommand;
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
 * Consumes {@code subscription.activated.v1} events from the {@code subscription.events} topic
 * and dispatches {@link ProvisionQuotaCommand} via the mediator (ADR-008).
 *
 * <p>This consumer is the entry point for feature 10.2.1 (Sprint 09 dependency). The
 * {@link com.telco.usage.application.handler.ProvisionQuotaCommandHandler} is a stub that logs a
 * warning until product-catalog integration is available.
 *
 * <p>Inbox dedup uses the Kafka record key as the message ID; the subscription-service outbox
 * relay sets the key to the event ID.
 */
@Component
public class SubscriptionActivatedEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SubscriptionActivatedEventConsumer.class);
    private static final String CONSUMER_NAME = "SubscriptionActivatedEventConsumer";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public SubscriptionActivatedEventConsumer(Mediator mediator,
                                              InboxService inboxService,
                                              ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "usage-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivated(ConsumerRecord<String, String> record) {
        String messageId = record.key() != null ? record.key() : "fallback-offset-" + record.offset();

        try {
            SubscriptionActivatedPayload payload =
                    objectMapper.readValue(record.value(), SubscriptionActivatedPayload.class);

            // Type/completeness filter FIRST, before touching the inbox: subscription.events
            // carries every subscription-aggregate event (msisdn.allocated.v1,
            // subscription.activation-failed.v1, subscription.suspended.v1, etc.), and Debezium's
            // outbox EventRouter sets the Kafka record KEY to the aggregate_id (subscriptionId) for
            // ALL of them - the same key subscription.activated.v1 carries for the same aggregate.
            // Calling inboxService.firstSeen(messageId, ...) before this check would let an earlier,
            // unrelated event for the same subscriptionId (e.g. msisdn.allocated.v1, which has no
            // tariffCode) permanently consume the dedup slot, so the real activation event is then
            // wrongly treated as a "duplicate" and quota is never provisioned. Matches the
            // (correctly ordered) sibling consumer, billing-service's
            // SubscriptionActivatedBillingConsumer.
            if (payload.subscriptionId() == null || payload.customerId() == null
                    || payload.tariffCode() == null) {
                LOGGER.debug("Ignoring non-activation subscription event messageId={}", messageId);
                return;
            }

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate subscription.activated.v1 messageId={}", messageId);
                return;
            }

            // activatedAt is published as epoch millis (subscription-service SubscriptionActivatedV1,
            // a long, matching the Avro timestamp-millis contract) - NOT an ISO-8601 string. Using
            // Instant.parse(...) here (bug found via live acceptance testing, 2026-07-06) threw
            // DateTimeParseException on every real message ("Text '1783...' could not be parsed at
            // index 0"), and since the inbox row is marked seen before this line runs, every Kafka
            // redelivery retry after the crash was then silently swallowed as a "duplicate" - quota
            // was never provisioned for any subscription, ever, in this environment.
            Instant activatedAt = payload.activatedAt() != null
                    ? Instant.ofEpochMilli(payload.activatedAt())
                    : Instant.now();

            ProvisionQuotaCommand command = new ProvisionQuotaCommand(
                    UUID.fromString(payload.subscriptionId()),
                    UUID.fromString(payload.customerId()),
                    payload.tariffCode(),
                    activatedAt);

            mediator.send(command);
            LOGGER.info("Dispatched ProvisionQuotaCommand subscriptionId={} tariffCode={}",
                    payload.subscriptionId(), payload.tariffCode());

        } catch (Exception e) {
            LOGGER.error("Failed to process subscription.activated.v1 messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException("subscription.activated.v1 processing failed", e);
        }
    }

    /** JSON payload DTO for {@code subscription.events} messages. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionActivatedPayload(
            String subscriptionId,
            String customerId,
            String tariffCode,
            Long activatedAt
    ) {
    }
}
