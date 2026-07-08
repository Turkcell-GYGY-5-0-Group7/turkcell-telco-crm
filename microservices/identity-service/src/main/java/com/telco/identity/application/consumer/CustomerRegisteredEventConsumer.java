package com.telco.identity.application.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.identity.application.command.LinkCustomerToUserCommand;
import com.telco.platform.inbox.InboxService;
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
 * Consumes {@code customer.registered.v1} from the {@code customer.events} topic and dispatches
 * {@link LinkCustomerToUserCommand} via the mediator to close the identity-to-customer linkage gap
 * for genuine self-service registrations (Section 14.1.1 ruling, ADR-008).
 *
 * <p><b>Event-type filtering happens before the inbox check.</b> {@code customer.events} carries every
 * customer-service aggregate event (also {@code customer.kyc-approved.v1}, {@code
 * customer.kyc-rejected.v1}, {@code customer.updated.v1}) and the Debezium EventRouter sets the Kafka
 * record key to the same {@code aggregate_id} (the customer id) for all of them, per the event catalog
 * (ADR-009). Calling {@link InboxService#firstSeen} before filtering by the {@code eventType} header
 * would let an unrelated event for the same customer permanently consume this consumer's dedup slot,
 * silently swallowing the real {@code customer.registered.v1} event on redelivery - the exact class of
 * bug already found and fixed in usage-service's {@code SubscriptionActivatedEventConsumer} and in
 * notification-service's {@code DomainEventNotificationConsumer} (which reads this same header for the
 * same reason).
 *
 * <p><b>Registration channel distinction.</b> Only self-service registrations carry a non-null {@code
 * registeredByUserId} (the Keycloak subject of the caller, set by {@code customer-service}'s
 * {@code RegisterCustomerCommandHandler}); agent/dealer-assisted registrations leave it null and are
 * intentionally never linked here - out of scope per the ruling until a future "claim my account" flow.
 *
 * <p><b>Idempotency.</b> Dedup uses the envelope {@code eventId} that {@code JacksonEventSerializer}
 * embeds as a top-level JSON field for every outbox-published event, giving a stable per-event key
 * that is immune to the key-collision problem above (falls back to the Kafka record key, then the
 * partition offset, if somehow absent).
 */
@Component
public class CustomerRegisteredEventConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CustomerRegisteredEventConsumer.class);
    private static final String CONSUMER_NAME = "CustomerRegisteredEventConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String CUSTOMER_REGISTERED = "customer.registered.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public CustomerRegisteredEventConsumer(Mediator mediator, InboxService inboxService,
                                            ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "customer.events", groupId = "identity-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onCustomerEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!CUSTOMER_REGISTERED.equals(eventType)) {
            return;
        }

        try {
            CustomerRegisteredPayload payload =
                    objectMapper.readValue(record.value(), CustomerRegisteredPayload.class);

            if (payload.registeredByUserId() == null || payload.registeredByUserId().isBlank()) {
                LOGGER.debug("Ignoring agent/dealer-assisted customer.registered.v1 customerId={} "
                        + "(no registeredByUserId - out of scope per Section 14.1.1 ruling)",
                        payload.customerId());
                return;
            }

            String messageId = payload.eventId() != null ? payload.eventId()
                    : (record.key() != null ? record.key() : "fallback-offset-" + record.offset());

            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Skipping duplicate customer.registered.v1 messageId={}", messageId);
                return;
            }

            mediator.send(new LinkCustomerToUserCommand(payload.registeredByUserId(),
                    UUID.fromString(payload.customerId())));

            LOGGER.info("Dispatched LinkCustomerToUserCommand customerId={} registeredByUserId={}",
                    payload.customerId(), payload.registeredByUserId());

        } catch (Exception e) {
            LOGGER.error("Failed to process customer.registered.v1: {}", e.getMessage(), e);
            throw new RuntimeException("customer.registered.v1 processing failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** JSON payload DTO for {@code customer.registered.v1} envelopes on {@code customer.events}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerRegisteredPayload(
            String customerId,
            String registeredByUserId,
            String eventId
    ) {
    }
}
