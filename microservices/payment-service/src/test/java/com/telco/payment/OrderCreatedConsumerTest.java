package com.telco.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.telco.payment.application.consumer.OrderCreatedEventConsumer;
import com.telco.payment.application.scheduler.PaymentRetryScheduler;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
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
 * Verifies the {@code order.created.v1} consumer's event-type filter (Feature 9.4.3 correctness
 * fix). The {@code order.events} topic now also carries {@code order.cancelled.v1} (saga
 * compensation); payment-service must charge ONLY on {@code order.created.v1} and ignore everything
 * else (fail closed on the {@code eventType} header).
 *
 * <p>The consumer is invoked directly with a constructed {@link ConsumerRecord} against the real
 * Spring context (real InboxService, Mediator, Testcontainers Postgres); only the PSP and retry
 * scheduler are mocked.
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
class OrderCreatedConsumerTest {

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
    OrderCreatedEventConsumer consumer;

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
    }

    private ConsumerRecord<String, String> orderEventRecord(String messageId, UUID orderId,
                                                            long offset, String eventType) {
        String json = "{\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"totalAmount\":49.99,"
                + "\"occurredAt\":\"2026-06-29T00:00:00Z\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order.events", 0, offset, messageId, json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private long paymentCount(UUID orderId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Long.class, orderId);
        return count == null ? 0L : count;
    }

    @Test
    void order_created_charges_a_payment() {
        UUID orderId = UUID.randomUUID();

        consumer.onOrderCreated(orderEventRecord("msg-created", orderId, 0L, "order.created.v1"));

        assertThat(paymentCount(orderId)).isEqualTo(1L);
        String status = jdbc.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        assertThat(status).isEqualTo("COMPLETED");
    }

    @Test
    void order_cancelled_on_the_same_topic_is_ignored_no_charge() {
        // order.cancelled.v1 (saga compensation) shares the order.events topic and carries the same
        // identifying fields, but must NEVER charge. Regression for the missing event-type filter.
        UUID orderId = UUID.randomUUID();

        consumer.onOrderCreated(orderEventRecord("msg-cancelled", orderId, 0L, "order.cancelled.v1"));

        assertThat(paymentCount(orderId)).isEqualTo(0L);
    }

    @Test
    void message_without_event_type_header_is_ignored_no_charge() {
        UUID orderId = UUID.randomUUID();

        consumer.onOrderCreated(orderEventRecord("msg-no-header", orderId, 0L, null));

        assertThat(paymentCount(orderId)).isEqualTo(0L);
    }
}
