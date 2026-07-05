package com.telco.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.consumer.SubscriptionActivationFailedEventConsumer;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.scheduler.PaymentRetryScheduler;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.mediator.Mediator;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the {@code subscription.activation-failed.v1} compensation consumer (Feature 9.4.3):
 * consuming the event refunds the COMPLETED payment for the saga's order, dispatching the existing
 * {@link com.telco.payment.application.command.RefundPaymentCommand} and emitting
 * {@code payment.refunded.v1}. Proves event-type filtering (fail closed), inbox + state-based
 * idempotency, and the safe no-op when no COMPLETED payment exists.
 *
 * <p>The consumer is invoked directly with a constructed {@link ConsumerRecord} against the real
 * Spring context (real InboxService, Mediator, OutboxService, Testcontainers Postgres) - no Kafka
 * broker is needed. Only the PSP and the retry scheduler are mocked.
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
class SubscriptionActivationFailedConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    PspAdapter pspAdapter;

    @MockitoBean
    PaymentRetryScheduler paymentRetryScheduler;

    @Autowired
    Mediator mediator;

    @Autowired
    SubscriptionActivationFailedEventConsumer consumer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws PspException {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM payment_attempts");
        jdbc.execute("DELETE FROM payments");

        when(pspAdapter.charge(any(), any(), any()))
                .thenReturn(new ChargeResult("TXN-" + UUID.randomUUID()));
        when(pspAdapter.refund(any(), any(), any()))
                .thenReturn(new ChargeResult("REFUND-" + UUID.randomUUID()));
    }

    /** Seeds a COMPLETED payment for the given order via the real charge pipeline (PSP mocked OK). */
    private UUID chargeCompleted(UUID orderId) {
        PaymentResponse response = mediator.send(new ChargePaymentCommand(
                orderId, UUID.randomUUID(), new BigDecimal("49.99"), null,
                orderId.toString(), "seed-" + orderId));
        return response.id();
    }

    private ConsumerRecord<String, String> activationFailedRecord(String messageId, UUID orderId,
                                                                  long offset) {
        return subscriptionEventRecord(messageId, orderId, offset, "subscription.activation-failed.v1");
    }

    /**
     * Builds a record on the shared {@code subscription.events} topic with the canonical
     * {@code eventType} header the Debezium EventRouter sets. The consumer must discriminate on the
     * header, not the payload.
     */
    private ConsumerRecord<String, String> subscriptionEventRecord(String messageId, UUID orderId,
                                                                   long offset, String eventType) {
        String json = "{\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"subscriptionId\":\"" + UUID.randomUUID() + "\","
                + "\"reason\":\"MSISDN_POOL_EXHAUSTED\","
                + "\"failedAt\":1719705600000}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("subscription.events", 0, offset, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private String status(UUID paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private long refundedEventCount(UUID orderId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'payment.refunded.v1' "
                        + "AND payload->>'orderId' = ?",
                Long.class, orderId.toString());
        return count == null ? 0L : count;
    }

    @Test
    void activation_failed_refunds_the_completed_payment_and_emits_refunded_event() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = chargeCompleted(orderId);
        assertThat(status(paymentId)).isEqualTo("COMPLETED");

        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-1", orderId, 0L));

        assertThat(status(paymentId)).isEqualTo("REFUNDED");
        assertThat(refundedEventCount(orderId)).isEqualTo(1L);
    }

    @Test
    void redelivery_with_same_message_id_is_idempotent_one_refund() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = chargeCompleted(orderId);

        // Same messageId twice -> inbox dedup makes the second a no-op.
        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-dup", orderId, 0L));
        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-dup", orderId, 1L));

        assertThat(status(paymentId)).isEqualTo("REFUNDED");
        assertThat(refundedEventCount(orderId)).isEqualTo(1L);
    }

    @Test
    void redelivery_with_new_message_id_is_still_a_no_op_when_already_refunded() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = chargeCompleted(orderId);

        // Distinct messageIds bypass inbox dedup; the not-COMPLETED guard keeps it idempotent and
        // never throws on the illegal COMPLETED->REFUNDED transition.
        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-a", orderId, 0L));
        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-b", orderId, 1L));

        assertThat(status(paymentId)).isEqualTo("REFUNDED");
        assertThat(refundedEventCount(orderId)).isEqualTo(1L);
    }

    @Test
    void wrong_event_type_on_the_same_topic_is_ignored() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = chargeCompleted(orderId);

        // subscription.activated.v1 / subscription.terminated.v1 share the topic but must not refund.
        consumer.onSubscriptionActivationFailed(
                subscriptionEventRecord("msg-activated", orderId, 0L, "subscription.activated.v1"));

        assertThat(status(paymentId)).isEqualTo("COMPLETED");
        assertThat(refundedEventCount(orderId)).isEqualTo(0L);
    }

    @Test
    void message_without_event_type_header_is_ignored() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = chargeCompleted(orderId);

        // No eventType header at all -> must be ignored (never act on payload shape alone).
        ConsumerRecord<String, String> noHeader = new ConsumerRecord<>(
                "subscription.events", 0, 0L, "msg-no-header",
                "{\"orderId\":\"" + orderId + "\",\"reason\":\"MSISDN_POOL_EXHAUSTED\"}");

        consumer.onSubscriptionActivationFailed(noHeader);

        assertThat(status(paymentId)).isEqualTo("COMPLETED");
        assertThat(refundedEventCount(orderId)).isEqualTo(0L);
    }

    @Test
    void no_completed_payment_for_order_is_a_safe_no_op() {
        // No payment was ever taken for this order -> nothing to compensate, no throw, no refund.
        UUID orderId = UUID.randomUUID();

        consumer.onSubscriptionActivationFailed(activationFailedRecord("msg-orphan", orderId, 0L));

        assertThat(refundedEventCount(orderId)).isEqualTo(0L);
        // Under atomic-inbox semantics dedup lives inside the RefundPaymentCommand transaction. The
        // orphan no-op dispatches no command, so no inbox row is written - and that is correct: a
        // redelivery simply re-evaluates the same read-side guard to the same no-op.
        Long inboxRows = jdbc.queryForObject(
                "SELECT count(*) FROM inbox_message WHERE message_id = ?", Long.class, "msg-orphan");
        assertThat(inboxRows).isEqualTo(0L);
    }
}
