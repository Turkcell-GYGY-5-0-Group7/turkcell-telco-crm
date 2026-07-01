package com.telco.order.routing;

import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Outbox routing regression GATE for order-service (Sprint 09 Feature 9.5, tech-lead mandate).
 *
 * <p>Background: a real-broker de-risk found that Debezium's EventRouter routes by the
 * {@code aggregate_type} column ({@code ${routedByValue}.events}); a PascalCase value like
 * {@code "Order"} routes to {@code Order.events}, which NO saga consumer subscribes to (they all
 * subscribe to lowercase {@code order.events} / {@code payment.events} / {@code subscription.events}),
 * silently breaking the saga. The existing consumer tests construct {@link org.apache.kafka.clients.consumer.ConsumerRecord}
 * with hardcoded lowercase topics and never exercise Debezium routing, so they could not catch this.
 *
 * <p>This test closes that gap end to end for the transport hop:
 * <ol>
 *   <li>drives the REAL {@code CreateOrderCommandHandler} through the mediator so the REAL
 *       {@code OutboxService} writes a real {@code outbox_event} row (no mocked outbox),</li>
 *   <li>reads the {@code aggregate_type} the producer actually persisted from the database,</li>
 *   <li>feeds that persisted value through the REAL Debezium {@link io.debezium.transforms.outbox.EventRouter}
 *       SMT (configured identically to the production connector) and asserts it routes to
 *       {@code order.events} and places the {@code eventType} header the consumers filter on.</li>
 * </ol>
 *
 * <p>Because step 3 consumes the value from step 2, a PascalCase regression of
 * {@code CreateOrderCommandHandler.OUTBOX_AGGREGATE_TYPE} makes the routed topic {@code Order.events}
 * and fails {@link #order_outbox_row_routes_to_the_lowercase_order_events_topic()} - proving routing
 * is DERIVED from the persisted {@code aggregate_type}, not a hardcoded consumer-test string.
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

    /** The lowercase topic every order-service saga consumer subscribes to (@KafkaListener topics). */
    private static final String EXPECTED_TOPIC = "order.events";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // Downstream validation clients are stubbed; the REAL OutboxService is intentionally NOT mocked
    // so the handler writes a real outbox row we can read the aggregate_type from.
    @MockitoBean
    CustomerServiceClient customerServiceClient;

    @MockitoBean
    ProductCatalogServiceClient productCatalogServiceClient;

    @Autowired
    Mediator mediator;

    @Autowired
    JdbcTemplate jdbc;

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TARIFF_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE saga_state, order_items, orders, outbox_event CASCADE");
        when(customerServiceClient.getCustomer(any()))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(any()))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "POSTPAID-001", "Postpaid Basic",
                        new BigDecimal("49.99"), "TRY", 3));
    }

    @Test
    void order_outbox_row_routes_to_the_lowercase_order_events_topic() {
        mediator.send(new CreateOrderCommand(
                CUSTOMER_ID, UUID.randomUUID().toString(),
                List.of(new OrderItemRequest(TARIFF_ID, 1)), "user-1"));

        // The producer wrote a real outbox row; read back what it actually persisted.
        String aggregateType = jdbc.queryForObject(
                "SELECT aggregate_type FROM outbox_event WHERE event_type = 'order.created.v1'",
                String.class);
        String eventType = jdbc.queryForObject(
                "SELECT event_type FROM outbox_event WHERE event_type = 'order.created.v1'",
                String.class);

        // Guard the invariant directly: a PascalCase regression fails here with a clear message.
        assertThat(aggregateType)
                .as("outbox aggregate_type must be lowercase so Debezium routes to %s", EXPECTED_TOPIC)
                .isEqualTo("order");

        // Prove routing is DERIVED from the persisted aggregate_type by running the real SMT on it.
        OutboxEventRouterHarness.Routed routed = OutboxEventRouterHarness.route(aggregateType, eventType);

        assertThat(routed.topic())
                .as("Debezium EventRouter must route the persisted aggregate_type to the topic "
                        + "order-service saga consumers subscribe to")
                .isEqualTo(EXPECTED_TOPIC);
        assertThat(routed.eventTypeHeader())
                .as("Debezium must place the event_type as the eventType header consumers fail closed on")
                .isEqualTo("order.created.v1");
    }

    @Test
    void a_pascalcase_aggregate_type_would_route_to_the_wrong_topic() {
        // Demonstrates the failure mode the gate catches: had a producer reverted to PascalCase,
        // the SMT routes to "Order.events" - a topic no consumer subscribes to - so the saga breaks.
        OutboxEventRouterHarness.Routed routed =
                OutboxEventRouterHarness.route("Order", "order.created.v1");

        assertThat(routed.topic()).isEqualTo("Order.events");
        assertThat(routed.topic()).isNotEqualTo(EXPECTED_TOPIC);
    }
}
