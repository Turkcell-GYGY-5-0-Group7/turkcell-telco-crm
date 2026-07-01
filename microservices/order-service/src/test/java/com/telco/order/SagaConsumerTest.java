package com.telco.order;

import com.telco.order.application.consumer.PaymentCompletedEventConsumer;
import com.telco.order.application.consumer.PaymentRefundedEventConsumer;
import com.telco.order.application.consumer.SubscriptionActivatedEventConsumer;
import com.telco.platform.outbox.OutboxService;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga consumer tests for order-service (Sprint 09 Feature 9.4). Each consumer is invoked directly
 * with a constructed {@link ConsumerRecord} against the real Spring context (real Mediator with the
 * atomic InboxBehavior, repositories, Testcontainers Postgres) - no Kafka broker is required to prove
 * the state transition, the saga_state advance, the eventType-header filter and idempotency on
 * redelivery. Redelivery with the same record key flows through the {@code IdempotentRequest} inbox
 * dedup (key = messageId), atomic inside the handler transaction.
 *
 * <p>{@link OutboxService} is mocked so the compensation path (which publishes order.cancelled.v1)
 * does not need a relay.
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
class SagaConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    OutboxService outboxService;

    @Autowired
    PaymentCompletedEventConsumer paymentCompletedConsumer;

    @Autowired
    SubscriptionActivatedEventConsumer subscriptionActivatedConsumer;

    @Autowired
    PaymentRefundedEventConsumer paymentRefundedConsumer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("TRUNCATE TABLE saga_state, order_items, orders CASCADE");
    }

    // --- seeding ---

    /** Inserts an order in the given status plus its ORDER_CREATED/PENDING saga_state row. */
    private UUID seedOrder(OrderStatusFixture status) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, customer_id, status, idempotency_key, user_id, "
                        + "total_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                orderId, UUID.randomUUID(), status.name(), UUID.randomUUID().toString(),
                "user-1", new java.math.BigDecimal("49.99"),
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO saga_state (id, order_id, step, status, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), orderId, "ORDER_CREATED", "PENDING",
                java.sql.Timestamp.from(Instant.now()));
        return orderId;
    }

    enum OrderStatusFixture { PENDING, CONFIRMED, FULFILLED, CANCELLED }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String sagaStep(UUID orderId) {
        return jdbc.queryForObject("SELECT step FROM saga_state WHERE order_id = ?", String.class, orderId);
    }

    private String sagaStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM saga_state WHERE order_id = ?", String.class, orderId);
    }

    private int inboxCount(String messageId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE message_id = ?", Integer.class, messageId);
        return n == null ? 0 : n;
    }

    // --- record builders ---

    private ConsumerRecord<String, String> paymentCompletedRecord(String messageId, UUID orderId, long offset) {
        return paymentEventRecord(messageId, orderId, offset, "payment.completed.v1");
    }

    private ConsumerRecord<String, String> paymentRefundedRecord(String messageId, UUID orderId, long offset) {
        return paymentEventRecord(messageId, orderId, offset, "payment.refunded.v1");
    }

    private ConsumerRecord<String, String> paymentEventRecord(String messageId, UUID orderId,
                                                              long offset, String eventType) {
        String json = "{\"paymentId\":\"" + UUID.randomUUID() + "\","
                + "\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"amount\":49.99,"
                + "\"reason\":\"PSP_REFUND\","
                + "\"occurredAt\":\"2026-06-30T00:00:00Z\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("payment.events", 0, offset, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<String, String> subscriptionActivatedRecord(String messageId, UUID orderId,
                                                                       long offset, String eventType) {
        String json = "{\"subscriptionId\":\"" + UUID.randomUUID() + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"msisdn\":\"905551234567\","
                + "\"tariffCode\":\"POSTPAID-001\","
                + "\"activatedAt\":1751241600000,"
                + "\"orderId\":\"" + orderId + "\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("subscription.events", 0, offset, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    // --- payment.completed.v1 -> CONFIRMED / saga PAID ---

    @Test
    void payment_completed_confirms_order_and_advances_saga() {
        UUID orderId = seedOrder(OrderStatusFixture.PENDING);

        paymentCompletedConsumer.onPaymentCompleted(paymentCompletedRecord("pc-1", orderId, 0L));

        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
        assertThat(sagaStep(orderId)).isEqualTo("PAYMENT_COMPLETED");
        assertThat(sagaStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void payment_completed_redelivery_is_idempotent() {
        UUID orderId = seedOrder(OrderStatusFixture.PENDING);

        paymentCompletedConsumer.onPaymentCompleted(paymentCompletedRecord("pc-dup", orderId, 0L));
        // Same messageId (atomic IdempotentRequest inbox dedup) and again a distinct messageId
        // (handler check-then-act): both no-ops.
        paymentCompletedConsumer.onPaymentCompleted(paymentCompletedRecord("pc-dup", orderId, 1L));
        paymentCompletedConsumer.onPaymentCompleted(paymentCompletedRecord("pc-other", orderId, 2L));

        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
        assertThat(sagaStatus(orderId)).isEqualTo("PAID");
        // The inbox recorded the first delivery once; the duplicate did not insert a second row.
        assertThat(inboxCount("pc-dup")).isEqualTo(1);
    }

    @Test
    void payment_completed_consumer_ignores_wrong_event_type() {
        UUID orderId = seedOrder(OrderStatusFixture.PENDING);

        paymentCompletedConsumer.onPaymentCompleted(
                paymentEventRecord("pc-wrong", orderId, 0L, "payment.failed.v1"));

        assertThat(orderStatus(orderId)).isEqualTo("PENDING");
        assertThat(sagaStatus(orderId)).isEqualTo("PENDING");
    }

    // --- subscription.activated.v1 -> FULFILLED / saga FULFILLED ---

    @Test
    void subscription_activated_fulfills_order_and_advances_saga() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        subscriptionActivatedConsumer.onSubscriptionActivated(
                subscriptionActivatedRecord("sa-1", orderId, 0L, "subscription.activated.v1"));

        assertThat(orderStatus(orderId)).isEqualTo("FULFILLED");
        assertThat(sagaStep(orderId)).isEqualTo("SUBSCRIPTION_ACTIVATED");
        assertThat(sagaStatus(orderId)).isEqualTo("FULFILLED");
    }

    @Test
    void subscription_activated_redelivery_is_idempotent() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        subscriptionActivatedConsumer.onSubscriptionActivated(
                subscriptionActivatedRecord("sa-dup", orderId, 0L, "subscription.activated.v1"));
        subscriptionActivatedConsumer.onSubscriptionActivated(
                subscriptionActivatedRecord("sa-dup", orderId, 1L, "subscription.activated.v1"));
        subscriptionActivatedConsumer.onSubscriptionActivated(
                subscriptionActivatedRecord("sa-other", orderId, 2L, "subscription.activated.v1"));

        assertThat(orderStatus(orderId)).isEqualTo("FULFILLED");
        assertThat(sagaStatus(orderId)).isEqualTo("FULFILLED");
        assertThat(inboxCount("sa-dup")).isEqualTo(1);
    }

    @Test
    void subscription_activated_consumer_ignores_wrong_event_type() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        subscriptionActivatedConsumer.onSubscriptionActivated(
                subscriptionActivatedRecord("sa-wrong", orderId, 0L, "subscription.suspended.v1"));

        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
    }

    // --- payment.refunded.v1 -> CANCELLED / saga COMPENSATED (system actor) ---

    @Test
    void payment_refunded_cancels_confirmed_order_and_advances_saga() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        paymentRefundedConsumer.onPaymentRefunded(paymentRefundedRecord("pr-1", orderId, 0L));

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(sagaStep(orderId)).isEqualTo("COMPENSATED");
        assertThat(sagaStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void payment_refunded_redelivery_is_idempotent() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        paymentRefundedConsumer.onPaymentRefunded(paymentRefundedRecord("pr-dup", orderId, 0L));
        // Redelivery (same and distinct messageId): the consumer pre-read guard sees the order is
        // already CANCELLED and skips dispatch; the handler's atomic inbox dedup + check-then-act are
        // the inner safety net. Either way the second/third deliveries are no-ops.
        paymentRefundedConsumer.onPaymentRefunded(paymentRefundedRecord("pr-dup", orderId, 1L));
        paymentRefundedConsumer.onPaymentRefunded(paymentRefundedRecord("pr-other", orderId, 2L));

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(sagaStatus(orderId)).isEqualTo("CANCELLED");
        // First delivery recorded its messageId in the inbox exactly once.
        assertThat(inboxCount("pr-dup")).isEqualTo(1);
    }

    @Test
    void payment_refunded_consumer_ignores_wrong_event_type() {
        UUID orderId = seedOrder(OrderStatusFixture.CONFIRMED);

        paymentRefundedConsumer.onPaymentRefunded(
                paymentEventRecord("pr-wrong", orderId, 0L, "payment.completed.v1"));

        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
    }
}
