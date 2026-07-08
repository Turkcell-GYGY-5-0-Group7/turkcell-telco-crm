package com.telco.identity.application.consumer;

import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.AuditLogRepository;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real integration test for {@link CustomerRegisteredEventConsumer} (Section 14.1.1 ruling:
 * identity-to-customer linkage gap).
 *
 * <p>Nothing is mocked except {@link KeycloakAdminClient} (never invoked by this code path; mocked
 * only to keep the Spring context isolated from a real Keycloak dependency, matching
 * {@code IdentityIntegrationTest}'s convention). Postgres (users table, platform outbox/inbox) and
 * Kafka run in Testcontainers; the test drives the ACTUAL consumer by publishing JSON envelopes to the
 * real {@code customer.events} topic with the {@code eventType} Kafka header - the shape the Debezium
 * EventRouter produces (event-catalog, ADR-009).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class CustomerRegisteredEventConsumerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Container
    static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer("apache/kafka:4.0.0");

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TOPIC = "customer.events";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String CUSTOMER_REGISTERED = "customer.registered.v1";

    private static Producer<String, String> producer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    KeycloakAdminClient keycloakAdminClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeAll
    static void startProducer() throws Exception {
        // Pre-create the topic so the consumer (which subscribes at startup) sees it immediately.
        Map<String, Object> adminCfg = new HashMap<>();
        adminCfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(adminCfg)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(cfg);
    }

    @AfterAll
    static void stopProducer() {
        if (producer != null) {
            producer.close();
        }
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM user_roles");
        userRepository.deleteAll();
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM outbox_event");

        // Ensure the real @KafkaListener container is running (base test profile disables
        // auto-startup) and has been assigned partitions before we publish, so no event is missed.
        listenerRegistry.getListenerContainers().forEach(c -> {
            if (!c.isRunning()) {
                c.start();
            }
        });
        await().atMost(TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
                .allMatch(MessageListenerContainer::isRunning));
    }

    @Test
    void selfServiceRegistrationLinksMatchingUser() {
        User user = userRepository.save(User.provision("kc-erin", "erin", "erin@example.com"));
        UUID customerId = UUID.randomUUID();

        publish(UUID.randomUUID().toString(), customerId.toString(), "kc-erin");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
        });
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "USER_CUSTOMER_LINKED".equals(log.getAction())
                        && user.getId().toString().equals(log.getEntityId()));
    }

    @Test
    void agentAssistedRegistrationWithNullRegisteredByUserIdStaysUnlinked() {
        User user = userRepository.save(User.provision("kc-frank", "frank", "frank@example.com"));
        UUID customerId = UUID.randomUUID();

        Map<String, String> payloadWithoutRegisteredByUserId = new HashMap<>();
        payloadWithoutRegisteredByUserId.put("eventId", UUID.randomUUID().toString());
        payloadWithoutRegisteredByUserId.put("customerId", customerId.toString());
        publishRaw(customerId.toString(), CUSTOMER_REGISTERED, payloadWithoutRegisteredByUserId);

        // Give the consumer time to (not) act; the user must stay unlinked - agent/dealer-assisted
        // registrations are explicitly out of scope per the ruling.
        await().during(Duration.ofSeconds(3)).atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isNull();
        });
    }

    @Test
    void noMatchingUserIsASafeNoOpAndDoesNotBlockTheConsumer() {
        // No User row exists for "kc-ghost" - simulates the confirmed provisioning gap (no
        // just-in-time user provisioning in this service): the consumer must not error or retry-loop,
        // and must keep processing subsequent messages normally.
        publish(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "kc-ghost");

        User laterUser = userRepository.save(User.provision("kc-grace", "grace", "grace@example.com"));
        UUID laterCustomerId = UUID.randomUUID();
        publish(UUID.randomUUID().toString(), laterCustomerId.toString(), "kc-grace");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(laterUser.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isEqualTo(laterCustomerId);
        });
    }

    @Test
    void duplicateEventIsDeduplicatedByInboxAndProcessedExactlyOnce() {
        User user = userRepository.save(User.provision("kc-holly", "holly", "holly@example.com"));
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        publish(eventId, customerId.toString(), "kc-holly");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
        });

        // Re-publish the SAME eventId (a different customerId would prove non-dedup, since a genuine
        // re-link would flip the value): if dedup fails, the link would move to otherCustomerId.
        publish(eventId, otherCustomerId.toString(), "kc-holly");

        await().during(Duration.ofSeconds(3)).atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
        });
        assertThat(auditLogRepository.findAll().stream()
                .filter(log -> "USER_CUSTOMER_LINKED".equals(log.getAction())
                        && user.getId().toString().equals(log.getEntityId()))
                .count())
                .isEqualTo(1L);
    }

    @Test
    void unrelatedCustomerEventTypeIsIgnoredAndDoesNotBurnTheInboxSlot() {
        User user = userRepository.save(User.provision("kc-ivan", "ivan", "ivan@example.com"));
        String sameAggregateId = UUID.randomUUID().toString();

        // customer.kyc-approved.v1 for the SAME aggregate id (Kafka record key) precedes the real
        // registration event - proving the eventType-header filter runs BEFORE the inbox check, else
        // this would burn the dedup slot and the real event below would be wrongly treated as a
        // duplicate (the class of bug already found and fixed in usage-service's
        // SubscriptionActivatedEventConsumer).
        publishRaw(sameAggregateId, "customer.kyc-approved.v1", Map.of("customerId", sameAggregateId));

        publish(UUID.randomUUID().toString(), sameAggregateId, "kc-ivan");

        await().atMost(TIMEOUT).untilAsserted(() -> {
            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getCustomerId()).isEqualTo(UUID.fromString(sameAggregateId));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void publish(String eventId, String customerId, String registeredByUserId) {
        Map<String, String> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("customerId", customerId);
        payload.put("registeredByUserId", registeredByUserId);
        publishRaw(customerId, CUSTOMER_REGISTERED, payload);
    }

    /** Publishes a JSON envelope to {@code customer.events} with the {@code eventType} header. */
    private void publishRaw(String key, String eventType, Map<String, String> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, json);
            record.headers().add(new RecordHeader(EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish test event", e);
        }
    }
}
