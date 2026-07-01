package com.telco.subscription.routing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.event.SubscriptionActivatedV1;
import com.telco.subscription.welcome.MockWelcomeSmsLog;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox routing regression GATE for subscription-service PLUS the AC-01 welcome-trigger proof
 * (Sprint 09 Feature 9.5). subscription-service is the saga hub, so this class carries both:
 *
 * <ol>
 *   <li><b>Routing gate (tech-lead mandate).</b> Drives the REAL {@code ActivateSubscriptionCommand}
 *       through the mediator so the REAL {@code OutboxService} persists the {@code msisdn.allocated.v1}
 *       and {@code subscription.activated.v1} rows, reads back their persisted {@code aggregate_type},
 *       and feeds it through the REAL Debezium {@link io.debezium.transforms.outbox.EventRouter} SMT
 *       (configured identically to the production connector). A PascalCase regression of
 *       {@code OUTBOX_AGGREGATE_TYPE} makes the routed topic {@code Subscription.events} and fails the
 *       assertion - proving routing is DERIVED from the persisted value, not a hardcoded test string.</li>
 *   <li><b>9.5.1 welcome trigger (Complexity S).</b> Asserts the persisted
 *       {@code subscription.activated.v1} payload carries exactly the data a welcome SMS needs
 *       (customerId, msisdn, tariffCode; orderId for correlation), then feeds the deserialized event
 *       into the {@link MockWelcomeSmsLog} stand-in (notification-service is Sprint 12) and asserts the
 *       welcome signal fires with the activated line's msisdn - proving AC-01 step 6 end to end from the
 *       real event, broker-free.</li>
 * </ol>
 *
 * <p>The existing consumer tests construct {@link org.apache.kafka.clients.consumer.ConsumerRecord}
 * with hardcoded lowercase topics and never exercise routing; this test closes that gap for the hub.
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

    /** The lowercase topic order-service (subscription.activated) subscribes to for the saga hop. */
    private static final String EXPECTED_TOPIC = "subscription.events";

    // The outbox serializer adds an envelope "eventId" alongside the payload fields; the welcome
    // consumer cares only about the business fields, so ignore unknown properties here.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");
    }

    /** Activates a subscription via the real command and returns the saga orderId used. */
    private UUID activate(UUID customerId, String tariffCode) {
        UUID orderId = UUID.randomUUID();
        mediator.send(new ActivateSubscriptionCommand(orderId, customerId, tariffCode, 1));
        return orderId;
    }

    // --- Routing gate ---

    @Test
    void subscription_activated_row_routes_to_the_lowercase_subscription_events_topic() {
        activate(UUID.randomUUID(), "TARIFF_BASIC");

        String aggregateType = jdbc.queryForObject(
                "SELECT aggregate_type FROM outbox_event WHERE event_type = 'subscription.activated.v1'",
                String.class);

        assertThat(aggregateType)
                .as("outbox aggregate_type must be lowercase so Debezium routes to %s", EXPECTED_TOPIC)
                .isEqualTo("subscription");

        OutboxEventRouterHarness.Routed routed =
                OutboxEventRouterHarness.route(aggregateType, "subscription.activated.v1");

        assertThat(routed.topic())
                .as("Debezium EventRouter must route the persisted aggregate_type to the topic "
                        + "the order-service subscription.activated consumer subscribes to")
                .isEqualTo(EXPECTED_TOPIC);
        assertThat(routed.eventTypeHeader())
                .as("Debezium must place the event_type as the eventType header consumers fail closed on")
                .isEqualTo("subscription.activated.v1");
    }

    @Test
    void every_subscription_outbox_row_routes_to_subscription_events() {
        // Activation writes TWO rows (msisdn.allocated.v1 + subscription.activated.v1); both must route
        // to subscription.events. Read every persisted aggregate_type and route it through the real SMT.
        activate(UUID.randomUUID(), "TARIFF_BASIC");

        jdbc.query("SELECT aggregate_type, event_type FROM outbox_event", rs -> {
            String aggregateType = rs.getString("aggregate_type");
            String eventType = rs.getString("event_type");
            OutboxEventRouterHarness.Routed routed = OutboxEventRouterHarness.route(aggregateType, eventType);
            assertThat(routed.topic())
                    .as("event %s must route to %s", eventType, EXPECTED_TOPIC)
                    .isEqualTo(EXPECTED_TOPIC);
            assertThat(routed.eventTypeHeader()).isEqualTo(eventType);
        });
    }

    @Test
    void a_pascalcase_aggregate_type_would_route_to_the_wrong_topic() {
        OutboxEventRouterHarness.Routed routed =
                OutboxEventRouterHarness.route("Subscription", "subscription.activated.v1");

        assertThat(routed.topic()).isEqualTo("Subscription.events");
        assertThat(routed.topic()).isNotEqualTo(EXPECTED_TOPIC);
    }

    // --- 9.5.1 welcome trigger ---

    @Test
    void activated_event_carries_the_data_a_welcome_sms_needs() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = activate(customerId, "TARIFF_BASIC");

        String payloadJson = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'subscription.activated.v1'",
                String.class);
        JsonNode payload = MAPPER.readTree(payloadJson);

        // The welcome SMS needs the recipient number, the customer, the tariff, and the saga orderId.
        assertThat(payload.get("customerId").asText()).isEqualTo(customerId.toString());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("tariffCode").asText()).isEqualTo("TARIFF_BASIC");
        assertThat(payload.get("msisdn").asText())
                .as("welcome SMS recipient number must be present and non-blank")
                .isNotBlank();
        // The msisdn on the event is the number actually allocated to the new subscription.
        String allocated = jdbc.queryForObject(
                "SELECT msisdn FROM subscriptions WHERE customer_id = ?", String.class, customerId);
        assertThat(payload.get("msisdn").asText()).isEqualTo(allocated);
    }

    @Test
    void welcome_sms_fires_from_the_activated_event() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = activate(customerId, "TARIFF_BASIC");

        String payloadJson = jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_type = 'subscription.activated.v1'",
                String.class);
        SubscriptionActivatedV1 event = MAPPER.readValue(payloadJson, SubscriptionActivatedV1.class);

        // Stand-in for the Sprint-12 notification-service consumer: derive + send the welcome SMS
        // purely from the event payload (AC-01 step 6), and assert it fired for the activated line.
        MockWelcomeSmsLog welcomeLog = new MockWelcomeSmsLog();
        MockWelcomeSmsLog.WelcomeSms welcome = welcomeLog.onSubscriptionActivated(event);

        assertThat(welcomeLog.sent()).hasSize(1);
        assertThat(welcome.customerId()).isEqualTo(customerId.toString());
        assertThat(welcome.orderId()).isEqualTo(orderId.toString());
        assertThat(welcome.tariffCode()).isEqualTo("TARIFF_BASIC");
        String allocated = jdbc.queryForObject(
                "SELECT msisdn FROM subscriptions WHERE customer_id = ?", String.class, customerId);
        assertThat(welcome.msisdn()).isEqualTo(allocated);
    }
}
