package com.telco.payment.routing;

import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.scheduler.PaymentRetryScheduler;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.mediator.Mediator;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Outbox routing regression GATE for payment-service (Sprint 09 Feature 9.5, tech-lead mandate).
 *
 * <p>See {@code order-service}'s counterpart for the full rationale. In short: a PascalCase
 * {@code aggregate_type} makes Debezium route to {@code Payment.events}, a topic no consumer
 * subscribes to (subscription and order both consume {@code payment.events}), silently breaking the
 * saga's charge -> activate and refund -> cancel hops. The existing consumer tests hardcode the
 * lowercase topic on the {@link org.apache.kafka.clients.consumer.ConsumerRecord} and never exercise
 * routing, so they cannot catch it.
 *
 * <p>This test drives the REAL {@code ChargePaymentCommandHandler} through the mediator so the REAL
 * {@code OutboxService} persists a real {@code outbox_event} row, reads back the persisted
 * {@code aggregate_type}, and feeds it through the REAL Debezium
 * {@link io.debezium.transforms.outbox.EventRouter} SMT. A PascalCase regression of
 * {@code ChargePaymentCommandHandler.OUTBOX_AGGREGATE_TYPE} fails the routing assertion.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
@ActiveProfiles("test")
@Testcontainers
class OutboxRoutingRegressionTest {

    /** The lowercase topic every payment-service saga consumer subscribes to (@KafkaListener topics). */
    private static final String EXPECTED_TOPIC = "payment.events";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // PSP is stubbed to succeed; the REAL OutboxService is intentionally NOT mocked so the handler
    // writes a real outbox row we can read the aggregate_type from.
    @MockitoBean
    PspAdapter pspAdapter;

    @MockitoBean
    PaymentRetryScheduler paymentRetryScheduler;

    @Autowired
    Mediator mediator;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws PspException {
        jdbc.execute("TRUNCATE TABLE payment_attempts, payments, outbox_event, inbox_message CASCADE");
        when(pspAdapter.charge(any(), any(), any()))
                .thenReturn(new ChargeResult("TXN-" + UUID.randomUUID()));
    }

    @Test
    void payment_outbox_row_routes_to_the_lowercase_payment_events_topic() {
        UUID orderId = UUID.randomUUID();
        String paymentRequestId = UUID.randomUUID().toString();
        mediator.send(new ChargePaymentCommand(
                orderId, UUID.randomUUID(), new BigDecimal("49.99"),
                paymentRequestId, UUID.randomUUID().toString()));

        String aggregateType = jdbc.queryForObject(
                "SELECT aggregate_type FROM outbox_event WHERE event_type = 'payment.completed.v1'",
                String.class);
        String eventType = jdbc.queryForObject(
                "SELECT event_type FROM outbox_event WHERE event_type = 'payment.completed.v1'",
                String.class);

        assertThat(aggregateType)
                .as("outbox aggregate_type must be lowercase so Debezium routes to %s", EXPECTED_TOPIC)
                .isEqualTo("payment");

        OutboxEventRouterHarness.Routed routed = OutboxEventRouterHarness.route(aggregateType, eventType);

        assertThat(routed.topic())
                .as("Debezium EventRouter must route the persisted aggregate_type to the topic "
                        + "payment-service saga consumers subscribe to")
                .isEqualTo(EXPECTED_TOPIC);
        assertThat(routed.eventTypeHeader())
                .as("Debezium must place the event_type as the eventType header consumers fail closed on")
                .isEqualTo("payment.completed.v1");
    }

    @Test
    void a_pascalcase_aggregate_type_would_route_to_the_wrong_topic() {
        OutboxEventRouterHarness.Routed routed =
                OutboxEventRouterHarness.route("Payment", "payment.completed.v1");

        assertThat(routed.topic()).isEqualTo("Payment.events");
        assertThat(routed.topic()).isNotEqualTo(EXPECTED_TOPIC);
    }
}
