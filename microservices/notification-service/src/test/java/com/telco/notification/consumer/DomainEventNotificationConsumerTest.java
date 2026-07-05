package com.telco.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.domain.Notification;
import com.telco.notification.service.NotificationService;
import com.telco.platform.inbox.InboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventNotificationConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private InboxService inboxService;

    private DomainEventNotificationConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DomainEventNotificationConsumer(notificationService, inboxService, objectMapper);
        // Lenient because "ignored event type" tests never reach inboxService or notificationService
        lenient().when(inboxService.firstSeen(anyString(), anyString())).thenReturn(true);
        lenient().when(notificationService.dispatch(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Notification.create("user", "code", "SMS", "{}"));
    }

    @Test
    void subscription_activated_dispatches_welcome_sms_for_customer() throws Exception {
        var record = record("subscription.events", "evt-1", "subscription.activated.v1",
                Map.of("eventId", "evt-1", "customerId", "cust-1",
                        "customerName", "Alice", "subscriptionId", "sub-1"));

        consumer.onSubscriptionEvent(record);

        verify(notificationService).dispatch(eq("cust-1"), eq("WELCOME"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void subscription_event_with_unrecognised_type_is_silently_ignored() throws Exception {
        var record = record("subscription.events", "key", "subscription.cancelled.v1",
                Map.of("customerId", "cust-1"));

        consumer.onSubscriptionEvent(record);

        verify(notificationService, never()).dispatch(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void duplicate_message_id_is_deduplicated_and_dispatch_is_skipped() throws Exception {
        when(inboxService.firstSeen(eq("evt-dup"), anyString())).thenReturn(false);
        var record = record("subscription.events", "key", "subscription.activated.v1",
                Map.of("eventId", "evt-dup", "customerId", "cust-1"));

        consumer.onSubscriptionEvent(record);

        verify(notificationService, never()).dispatch(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void customer_kyc_approved_dispatches_kyc_approved_sms() throws Exception {
        var record = record("customer.events", "evt-2", "customer.kyc-approved.v1",
                Map.of("eventId", "evt-2", "customerId", "cust-2"));

        consumer.onCustomerEvent(record);

        verify(notificationService).dispatch(eq("cust-2"), eq("KYC_APPROVED"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void customer_kyc_rejected_dispatches_kyc_rejected_sms_with_reason() throws Exception {
        var record = record("customer.events", "evt-3", "customer.kyc-rejected.v1",
                Map.of("eventId", "evt-3", "customerId", "cust-3", "reason", "document_expired"));

        consumer.onCustomerEvent(record);

        verify(notificationService).dispatch(eq("cust-3"), eq("KYC_REJECTED"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void customer_event_with_unrecognised_type_is_silently_ignored() throws Exception {
        var record = record("customer.events", "key", "customer.profile-updated.v1",
                Map.of("customerId", "cust-1"));

        consumer.onCustomerEvent(record);

        verify(notificationService, never()).dispatch(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void invoice_generated_dispatches_invoice_email() throws Exception {
        var record = record("invoice.events", "evt-4", "invoice.generated.v1",
                Map.of("eventId", "evt-4", "customerId", "cust-4",
                        "invoiceId", "inv-1", "grandTotal", "150.00", "currency", "TRY"));

        consumer.onInvoiceEvent(record);

        verify(notificationService).dispatch(eq("cust-4"), eq("INVOICE_GENERATED"), eq("EMAIL"), any(), eq("en"));
    }

    @Test
    void invoice_event_with_unrecognised_type_is_silently_ignored() throws Exception {
        var record = record("invoice.events", "key", "invoice.paid.v1",
                Map.of("customerId", "cust-1"));

        consumer.onInvoiceEvent(record);

        verify(notificationService, never()).dispatch(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void quota_threshold_reached_dispatches_quota_80_percent_sms() throws Exception {
        var record = record("quota.events", "evt-5", "quota.threshold-reached.v1",
                Map.of("eventId", "evt-5", "customerId", "cust-5", "subscriptionId", "sub-5"));

        consumer.onQuotaEvent(record);

        verify(notificationService).dispatch(eq("cust-5"), eq("QUOTA_80_PERCENT"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void quota_exceeded_dispatches_quota_exceeded_sms() throws Exception {
        var record = record("quota.events", "evt-6", "quota.exceeded.v1",
                Map.of("eventId", "evt-6", "customerId", "cust-6", "subscriptionId", "sub-6"));

        consumer.onQuotaEvent(record);

        verify(notificationService).dispatch(eq("cust-6"), eq("QUOTA_EXCEEDED"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void quota_threshold_reached_without_customer_id_falls_back_to_unknown() throws Exception {
        // Simulates an in-flight quota.threshold-reached.v1 message produced before the customerId
        // field existed (rolling-upgrade compatibility, ADR-019): must not throw, falls back safely.
        var record = record("quota.events", "evt-5b", "quota.threshold-reached.v1",
                Map.of("eventId", "evt-5b", "subscriptionId", "sub-5b"));

        consumer.onQuotaEvent(record);

        verify(notificationService).dispatch(eq("unknown"), eq("QUOTA_80_PERCENT"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void ticket_opened_dispatches_ticket_opened_sms_to_customer() throws Exception {
        var record = record("ticket.events", "evt-7", "ticket.opened.v1",
                Map.of("eventId", "evt-7", "customerId", "cust-7",
                        "ticketId", "tkt-1", "assignedTeam", "billing-support"));

        consumer.onTicketEvent(record);

        verify(notificationService).dispatch(eq("cust-7"), eq("TICKET_OPENED"), eq("SMS"), any(), eq("en"));
    }

    @Test
    void ticket_event_with_unrecognised_type_is_silently_ignored() throws Exception {
        var record = record("ticket.events", "key", "ticket.resolved.v1",
                Map.of("customerId", "cust-1"));

        consumer.onTicketEvent(record);

        verify(notificationService, never()).dispatch(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void malformed_json_payload_does_not_throw_and_falls_back_to_unknown_customer() {
        var record = new ConsumerRecord<String, String>("subscription.events", 0, 0L, "key", "not-json{{{{");
        record.headers().add(new RecordHeader("eventType",
                "subscription.activated.v1".getBytes(StandardCharsets.UTF_8)));

        // parsePayload catches the parse error and returns Map.of(), so customerId defaults to "unknown"
        consumer.onSubscriptionEvent(record);

        verify(notificationService).dispatch(eq("unknown"), eq("WELCOME"), eq("SMS"), any(), eq("en"));
    }

    private ConsumerRecord<String, String> record(String topic, String key,
                                                   String eventType, Map<String, ?> payload)
            throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        ConsumerRecord<String, String> r = new ConsumerRecord<>(topic, 0, 0L, key, json);
        r.headers().add(new RecordHeader("eventType",
                eventType.getBytes(StandardCharsets.UTF_8)));
        return r;
    }
}
