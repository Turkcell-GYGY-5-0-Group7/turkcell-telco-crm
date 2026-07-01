package com.telco.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.service.NotificationService;
import com.telco.platform.inbox.InboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Single consumer for all domain events that trigger notifications.
 * Maps each event type to a (templateCode, channel, userId) combination.
 */
@Component
public class DomainEventNotificationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainEventNotificationConsumer.class);
    private static final String CONSUMER_NAME = "notification-service";

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

    @KafkaListener(topics = "subscription.activated.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onSubscriptionActivated(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "customerName", payload.getOrDefault("customerName", customerId),
                    "subscriptionId", payload.getOrDefault("subscriptionId", "")
            );
            notificationService.dispatch(customerId, "WELCOME", "SMS", vars, "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing subscription.activated.v1", ex);
        }
    }

    @KafkaListener(topics = "customer.kyc-approved.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onKycApproved(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            notificationService.dispatch(customerId, "KYC_APPROVED", "SMS",
                    Map.of("customerId", customerId), "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing customer.kyc-approved.v1", ex);
        }
    }

    @KafkaListener(topics = "customer.kyc-rejected.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onKycRejected(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            notificationService.dispatch(customerId, "KYC_REJECTED", "SMS",
                    Map.of("customerId", customerId,
                            "reason", payload.getOrDefault("reason", "unspecified")), "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing customer.kyc-rejected.v1", ex);
        }
    }

    @KafkaListener(topics = "invoice.generated.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onInvoiceGenerated(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "customerName", customerId,
                    "invoiceId", payload.getOrDefault("invoiceId", ""),
                    "amount", payload.getOrDefault("grandTotal", "0"),
                    "currency", payload.getOrDefault("currency", "TRY")
            );
            notificationService.dispatch(customerId, "INVOICE_GENERATED", "EMAIL", vars, "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing invoice.generated.v1", ex);
        }
    }

    @KafkaListener(topics = "quota.threshold-reached.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onQuotaThresholdReached(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            notificationService.dispatch(customerId, "QUOTA_80_PERCENT", "SMS",
                    Map.of("subscriptionId", payload.getOrDefault("subscriptionId", "")), "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing quota.threshold-reached.v1", ex);
        }
    }

    @KafkaListener(topics = "quota.exceeded.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onQuotaExceeded(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            notificationService.dispatch(customerId, "QUOTA_EXCEEDED", "SMS",
                    Map.of("subscriptionId", payload.getOrDefault("subscriptionId", "")), "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing quota.exceeded.v1", ex);
        }
    }

    @KafkaListener(topics = "ticket.opened.v1", groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTicketOpened(ConsumerRecord<String, String> record) {
        if (!inboxService.firstSeen(record.key() + ":" + record.offset(), CONSUMER_NAME)) return;
        try {
            Map<String, String> payload = parsePayload(record.value());
            String customerId = payload.getOrDefault("customerId", "unknown");
            Map<String, String> vars = Map.of(
                    "ticketId", payload.getOrDefault("ticketId", ""),
                    "assignedTeam", payload.getOrDefault("assignedTeam", "support")
            );
            notificationService.dispatch(customerId, "TICKET_OPENED", "SMS", vars, "en");
        } catch (Exception ex) {
            LOGGER.error("Failed processing ticket.opened.v1", ex);
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
}
