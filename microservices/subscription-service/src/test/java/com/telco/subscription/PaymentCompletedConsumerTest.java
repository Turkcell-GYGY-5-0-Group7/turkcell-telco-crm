package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.subscription.application.consumer.PaymentCompletedEventConsumer;
import com.telco.subscription.infrastructure.client.OrderClientResponse;
import com.telco.subscription.infrastructure.client.OrderItemClientResponse;
import com.telco.subscription.infrastructure.client.OrderLookupRejectedException;
import com.telco.subscription.infrastructure.client.OrderServiceClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code payment.completed.v1} consumer (feature 9.4.1): it makes one sync hop to
 * order-service for the tariff snapshot, activates the subscription, and splits transient vs terminal
 * lookup failures. order-service is stubbed via {@link MockitoBean}; no broker is needed (the consumer
 * is invoked directly with a constructed {@link ConsumerRecord}).
 */
@SpringBootTest(
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
@ActiveProfiles("test")
@Testcontainers
class PaymentCompletedConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PaymentCompletedEventConsumer consumer;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    OrderServiceClient orderServiceClient;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");
    }

    private ConsumerRecord<String, String> paymentCompletedRecord(String messageId, UUID orderId,
                                                                  UUID customerId, long offset,
                                                                  String eventType) {
        String json = "{\"paymentId\":\"" + UUID.randomUUID() + "\","
                + "\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + customerId + "\","
                + "\"amount\":49.99,"
                + "\"occurredAt\":\"2026-06-30T00:00:00Z\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("payment.events", 0, offset, messageId, json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private OrderClientResponse singleItemOrder(UUID customerId) {
        return new OrderClientResponse(customerId, "CONFIRMED",
                List.of(new OrderItemClientResponse("TARIFF_BASIC", 1)));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void payment_completed_activates_the_subscription_and_emits_activated_with_orderId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenReturn(singleItemOrder(customerId));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-1", orderId, customerId, 0L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions WHERE customer_id = ? AND status = 'ACTIVE'",
                customerId)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activated.v1' "
                + "AND payload->>'orderId' = ?", orderId.toString())).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM msisdn_pool WHERE status = 'ALLOCATED'")).isEqualTo(1L);
    }

    @Test
    void redelivery_is_idempotent() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenReturn(singleItemOrder(customerId));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-dup", orderId, customerId, 0L, "payment.completed.v1"));
        consumer.onPaymentCompleted(paymentCompletedRecord("msg-dup", orderId, customerId, 1L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions WHERE customer_id = ?", customerId)).isEqualTo(1L);
    }

    @Test
    void wrong_event_type_is_ignored() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-wrong", orderId, customerId, 0L, "payment.refunded.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
    }

    @Test
    void missing_event_type_header_is_ignored() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-noheader", orderId, customerId, 0L, null));

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
    }

    @Test
    void order_not_found_emits_activation_failed_and_does_not_activate() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenThrow(new ResourceNotFoundException("Order not found: " + orderId));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-404", orderId, customerId, 0L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activation-failed.v1' "
                + "AND payload->>'orderId' = ? AND payload->>'reason' = 'ORDER_NOT_FOUND'",
                orderId.toString())).isEqualTo(1L);
    }

    @Test
    void order_lookup_rejected_4xx_emits_activation_failed_with_order_lookup_rejected() {
        // A non-404 4xx (e.g. 401/403/400/409/422) is a contract/auth defect that cannot heal by
        // redelivery, so it must fail closed to compensation (TERMINAL), not dead-loop the listener.
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId))
                .thenThrow(new OrderLookupRejectedException("order-service rejected lookup", null));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-4xx", orderId, customerId, 0L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activation-failed.v1' "
                + "AND payload->>'orderId' = ? AND payload->>'reason' = 'ORDER_LOOKUP_REJECTED'",
                orderId.toString())).isEqualTo(1L);
    }

    @Test
    void redelivery_with_a_new_message_id_is_idempotent_by_order() {
        // Activation idempotency is keyed by orderId (one order = one activation), so a redelivery with
        // a DIFFERENT Kafka messageId for the same order must still activate at most once.
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenReturn(singleItemOrder(customerId));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-x", orderId, customerId, 0L, "payment.completed.v1"));
        consumer.onPaymentCompleted(paymentCompletedRecord("msg-y", orderId, customerId, 1L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions WHERE customer_id = ?", customerId)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activated.v1' "
                + "AND payload->>'orderId' = ?", orderId.toString())).isEqualTo(1L);
    }

    @Test
    void transient_order_failure_rethrows_without_activating_or_recording_inbox() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenThrow(new DependencyFailureException("order-service down", null));

        ConsumerRecord<String, String> record =
                paymentCompletedRecord("msg-transient", orderId, customerId, 0L, "payment.completed.v1");
        assertThatThrownBy(() -> consumer.onPaymentCompleted(record))
                .isInstanceOf(DependencyFailureException.class);

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activation-failed.v1'"))
                .isEqualTo(0L);
        // Inbox must NOT be recorded, so a redelivery re-attempts the hop.
        assertThat(count("SELECT count(*) FROM inbox_message")).isEqualTo(0L);
    }

    @Test
    void multi_item_order_emits_activation_failed() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(orderServiceClient.getOrder(orderId)).thenReturn(new OrderClientResponse(customerId, "CONFIRMED",
                List.of(new OrderItemClientResponse("TARIFF_BASIC", 1),
                        new OrderItemClientResponse("TARIFF_PLUS", 1))));

        consumer.onPaymentCompleted(paymentCompletedRecord("msg-multi", orderId, customerId, 0L, "payment.completed.v1"));

        assertThat(count("SELECT count(*) FROM subscriptions")).isEqualTo(0L);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activation-failed.v1' "
                + "AND payload->>'reason' = 'UNSUPPORTED_MULTI_ITEM_ORDER'")).isEqualTo(1L);
    }
}
