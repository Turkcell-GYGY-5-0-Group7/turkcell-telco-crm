package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.consumer.PaymentFailedEventConsumer;
import java.nio.charset.StandardCharsets;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code payment.failed.v1} consumer (feature 9.3.2): consuming the event suspends the
 * customer's ACTIVE subscription and is idempotent on redelivery (inbox dedup + ACTIVE-only suspend).
 *
 * <p>The consumer is invoked directly with a constructed {@link ConsumerRecord} against the real
 * Spring context (real InboxService, Mediator, Testcontainers Postgres) - no Kafka broker is needed
 * to prove the suspend + idempotency behavior.
 */
@SpringBootTest(
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                // Do not start the @KafkaListener container; we call the consumer method directly.
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
@ActiveProfiles("test")
@Testcontainers
class PaymentFailedConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    Mediator mediator;

    @Autowired
    PaymentFailedEventConsumer consumer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");
    }

    private ConsumerRecord<String, String> paymentFailedRecord(String messageId, UUID customerId, long offset) {
        return paymentEventRecord(messageId, customerId, offset, "payment.failed.v1");
    }

    /**
     * Builds a record on the shared {@code payment.events} topic with the canonical {@code eventType}
     * header the Debezium EventRouter sets. The JSON shape is the same across payment event types; the
     * consumer must discriminate on the header, not the payload.
     */
    private ConsumerRecord<String, String> paymentEventRecord(String messageId, UUID customerId,
                                                              long offset, String eventType) {
        String json = "{\"paymentId\":\"" + UUID.randomUUID() + "\","
                + "\"orderId\":\"" + UUID.randomUUID() + "\","
                + "\"customerId\":\"" + customerId + "\","
                + "\"amount\":49.99,"
                + "\"reason\":\"PSP_DECLINED\","
                + "\"occurredAt\":\"2026-06-29T00:00:00Z\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("payment.events", 0, offset, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private String status(UUID subscriptionId) {
        return jdbc.queryForObject(
                "SELECT status FROM subscriptions WHERE id = ?", String.class, subscriptionId);
    }

    @Test
    void consuming_payment_failed_suspends_the_active_subscription() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), customerId, "TARIFF_BASIC", 1));

        consumer.onPaymentFailed(paymentFailedRecord("msg-1", customerId, 0L));

        assertThat(status(subscriptionId)).isEqualTo("SUSPENDED");
        Long suspended = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.suspended.v1' "
                        + "AND aggregate_id = ? AND payload->>'reason' = 'NON_PAYMENT'",
                Long.class, subscriptionId.toString());
        assertThat(suspended).isEqualTo(1L);
    }

    @Test
    void payment_completed_on_the_same_topic_does_not_suspend() {
        // payment.completed.v1 carries a customerId and the same JSON shape as a failure, but must
        // NEVER suspend the line. The consumer discriminates on the eventType header. (Regression for
        // the bug where filtering on customerId alone suspended customers on successful payment.)
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), customerId, "TARIFF_BASIC", 1));

        consumer.onPaymentFailed(
                paymentEventRecord("msg-completed", customerId, 0L, "payment.completed.v1"));

        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
        Long suspended = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.suspended.v1'",
                Long.class);
        assertThat(suspended).isEqualTo(0L);
    }

    @Test
    void message_without_event_type_header_is_ignored() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), customerId, "TARIFF_BASIC", 1));

        // No eventType header at all -> must be ignored (never act on payload shape alone).
        ConsumerRecord<String, String> noHeader = new ConsumerRecord<>(
                "payment.events", 0, 0L, "msg-no-header",
                "{\"customerId\":\"" + customerId + "\",\"reason\":\"PSP_DECLINED\"}");

        consumer.onPaymentFailed(noHeader);

        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
    }

    @Test
    void redelivery_is_idempotent() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), customerId, "TARIFF_BASIC", 1));

        // Same messageId twice -> inbox dedup makes the second a no-op.
        consumer.onPaymentFailed(paymentFailedRecord("msg-dup", customerId, 0L));
        consumer.onPaymentFailed(paymentFailedRecord("msg-dup", customerId, 1L));

        assertThat(status(subscriptionId)).isEqualTo("SUSPENDED");
        Long suspended = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.suspended.v1' "
                        + "AND aggregate_id = ?",
                Long.class, subscriptionId.toString());
        assertThat(suspended).isEqualTo(1L);
    }

    @Test
    void redelivery_with_new_message_id_is_still_a_no_op_when_already_suspended() {
        UUID customerId = UUID.randomUUID();
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), customerId, "TARIFF_BASIC", 1));

        // Distinct messageIds bypass inbox dedup; the ACTIVE-only suspend keeps it idempotent.
        consumer.onPaymentFailed(paymentFailedRecord("msg-a", customerId, 0L));
        consumer.onPaymentFailed(paymentFailedRecord("msg-b", customerId, 1L));

        assertThat(status(subscriptionId)).isEqualTo("SUSPENDED");
        Long suspended = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.suspended.v1' "
                        + "AND aggregate_id = ?",
                Long.class, subscriptionId.toString());
        assertThat(suspended).isEqualTo(1L);
    }
}
