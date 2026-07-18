package com.telco.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.service.NotificationService;
import com.telco.platform.inbox.InboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Single consumer for all domain events that trigger notifications.
 *
 * <p>Producers publish JSON via the outbox; the Debezium {@code EventRouter} routes each outbox row
 * to the {@code <aggregate_type>.events} topic (lowercase domain) and writes the event type into the
 * {@code eventType} Kafka header (event-catalog, ADR-009). Each {@code <domain>.events} topic carries
 * every event type of that domain, so this consumer subscribes per domain topic and dispatches by
 * reading the {@code eventType} header - never by payload shape. Unrecognised types are ignored.
 *
 * <p>Idempotency: dedup uses the envelope {@code eventId} embedded in the JSON payload by
 * {@code JacksonEventSerializer}, checked against the platform {@link InboxService} (ADR-005).
 */
@Component
public class DomainEventNotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainEventNotificationConsumer.class);
    private static final String CONSUMER_NAME = "notification-service";
    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";

    // Event types (match the eventType header values).
    private static final String SUBSCRIPTION_ACTIVATED = "subscription.activated.v1";
    private static final String CUSTOMER_KYC_APPROVED = "customer.kyc-approved.v1";
    private static final String CUSTOMER_KYC_REJECTED = "customer.kyc-rejected.v1";
    private static final String INVOICE_GENERATED = "invoice.generated.v1";
    private static final String QUOTA_THRESHOLD_REACHED = "quota.threshold-reached.v1";
    private static final String QUOTA_EXCEEDED = "quota.exceeded.v1";
    private static final String TICKET_OPENED = "ticket.opened.v1";
    private static final String FRAUD_CASE_OPENED = "fraud.case-opened.v1";

    /** Internal ops/security responder queue - not a customer id; keys the ops-facing alert. */
    private static final String OPS_ALERT_RECIPIENT = "security-ops";

    private final NotificationService notificationService;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public DomainEventNotificationConsumer(NotificationService notificationService,
                                            InboxService inboxService,
                                            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "subscription.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!SUBSCRIPTION_ACTIVATED.equals(eventType)) {
            return;
        }
        dispatch(record, eventType, payload -> {
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "customerName", payload.getOrDefault("customerName", customerId),
                    "subscriptionId", payload.getOrDefault("subscriptionId", ""));
            notificationService.dispatch(customerId, "WELCOME", "SMS", vars, "en");
        });
    }

    @KafkaListener(topics = "customer.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onCustomerEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (CUSTOMER_KYC_APPROVED.equals(eventType)) {
            dispatch(record, eventType, payload -> {
                String customerId = payload.getOrDefault("customerId", "unknown");
                notificationService.dispatch(customerId, "KYC_APPROVED", "SMS",
                        Map.of("customerId", customerId), "en");
            });
        } else if (CUSTOMER_KYC_REJECTED.equals(eventType)) {
            dispatch(record, eventType, payload -> {
                String customerId = payload.getOrDefault("customerId", "unknown");
                notificationService.dispatch(customerId, "KYC_REJECTED", "SMS",
                        Map.of("customerId", customerId,
                                "reason", payload.getOrDefault("reason", "unspecified")), "en");
            });
        }
    }

    @KafkaListener(topics = "invoice.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onInvoiceEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!INVOICE_GENERATED.equals(eventType)) {
            return;
        }
        dispatch(record, eventType, payload -> {
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "customerName", customerId,
                    "invoiceId", payload.getOrDefault("invoiceId", ""),
                    "amount", payload.getOrDefault("grandTotal", "0"),
                    "currency", payload.getOrDefault("currency", "TRY"));
            notificationService.dispatch(customerId, "INVOICE_GENERATED", "EMAIL", vars, "en");
        });
    }

    @KafkaListener(topics = "quota.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onQuotaEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (QUOTA_THRESHOLD_REACHED.equals(eventType)) {
            dispatch(record, eventType, payload -> {
                String customerId = payload.getOrDefault("customerId", "unknown");
                notificationService.dispatch(customerId, "QUOTA_80_PERCENT", "SMS",
                        Map.of("subscriptionId", payload.getOrDefault("subscriptionId", "")), "en");
            });
        } else if (QUOTA_EXCEEDED.equals(eventType)) {
            dispatch(record, eventType, payload -> {
                String customerId = payload.getOrDefault("customerId", "unknown");
                notificationService.dispatch(customerId, "QUOTA_EXCEEDED", "SMS",
                        Map.of("subscriptionId", payload.getOrDefault("subscriptionId", "")), "en");
            });
        }
    }

    @KafkaListener(topics = "ticket.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTicketEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!TICKET_OPENED.equals(eventType)) {
            return;
        }
        dispatch(record, eventType, payload -> {
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "ticketId", payload.getOrDefault("ticketId", ""),
                    "assignedTeam", payload.getOrDefault("assignedTeam", "support"));
            notificationService.dispatch(customerId, "TICKET_OPENED", "SMS", vars, "en");
        });
    }

    /**
     * Raises a single internal ops/security alert when a fraud case is opened
     * ({@code fraud.case-opened.v1}, fraud-service, ADR-029 Section 5). The alert goes to the internal
     * {@code OPS_ALERT} channel (distinct from customer-facing SMS/email) so a security/ops responder is
     * aware of the case independently of the ticket-service queue. Informational only: it triggers no
     * subscription-service call and no automated suspension. Idempotent via the shared inbox
     * {@link #dispatch} helper - a replayed event raises no second alert.
     */
    @KafkaListener(topics = "fraud.events", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onFraudEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!FRAUD_CASE_OPENED.equals(eventType)) {
            return;
        }
        dispatch(record, eventType, payload -> {
            Map<String, String> vars = Map.of(
                    "caseId", payload.getOrDefault("caseId", ""),
                    "customerId", payload.getOrDefault("customerId", "unknown"),
                    "severity", payload.getOrDefault("highestSeverity", "unknown"));
            notificationService.dispatch(OPS_ALERT_RECIPIENT, "FRAUD_CASE_OPENED", "OPS_ALERT", vars, "en");
        });
    }

    /**
     * Runs the inbox-idempotent dispatch: dedup on the envelope {@code eventId}, then apply the
     * given action to the parsed payload. Parsing/dispatch failures are logged (never rethrown) so a
     * poison notification does not stall the partition.
     */
    private void dispatch(ConsumerRecord<String, String> record, String eventType,
                          java.util.function.Consumer<Map<String, String>> action) {
        Map<String, String> payload = parsePayload(record.value());
        String eventId = payload.get("eventId");
        String messageId = eventId != null ? eventId
                : (record.key() != null ? record.key() : "fallback-offset-" + record.offset());
        if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
            LOGGER.debug("Duplicate {} messageId={}", eventType, messageId);
            return;
        }
        try {
            action.accept(payload);
        } catch (Exception ex) {
            LOGGER.error("Failed processing {} messageId={}", eventType, messageId, ex);
        }
    }

    private Map<String, String> parsePayload(String json) {
        try {
            Map<?, ?> raw = objectMapper.readValue(json, HashMap.class);
            Map<String, String> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
            return result;
        } catch (Exception ex) {
            LOGGER.warn("Could not parse event payload: {}", json);
            return Map.of();
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
