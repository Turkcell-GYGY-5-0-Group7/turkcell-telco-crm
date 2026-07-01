package com.telco.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.domain.CommunicationPreference;
import com.telco.notification.domain.Notification;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
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
 * Real integration test for the notification engagement path (12.6.1, 12.3.1, 12.2.1).
 *
 * <p>Nothing is mocked: MongoDB (primary store), Postgres (platform outbox/inbox), and Kafka all run
 * in Testcontainers. The test drives the ACTUAL {@link com.telco.notification.consumer
 * .DomainEventNotificationConsumer} by publishing JSON envelopes to the real {@code <domain>.events}
 * topics with the {@code eventType} Kafka header (the shape the Debezium EventRouter produces). The
 * router's own routing is covered by the Sprint 09 regression gate; here we prove the consume ->
 * template -> channel -> Mongo persistence -> outbox path and the inbox once-only guarantee.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
                // Listener auto-startup stays OFF (base test profile): the real @KafkaListener
                // containers are started explicitly in setUp() AFTER the topics are created, so the
                // consumer never subscribes to a not-yet-existent topic (avoids metadata-refresh flakiness).
        }
)
@ActiveProfiles("test")
@Testcontainers
class NotificationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container
    private static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer("apache/kafka:4.0.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Raw producer harness. The service itself is consumer-only (no producer autoconfigured), so the
     * test owns a plain String producer pointed at the Kafka container to publish domain-event
     * envelopes with the eventType header - exactly the shape the Debezium EventRouter emits.
     */
    private static Producer<String, String> producer;

    private static final List<String> TOPICS = List.of(
            "subscription.events", "customer.events", "invoice.events", "quota.events", "ticket.events");

    @BeforeAll
    static void startProducer() throws Exception {
        // Pre-create the domain topics so the consumer (which subscribes at startup) sees them
        // immediately; a topic created AFTER subscription would only be discovered on the consumer's
        // metadata refresh, causing flaky misses.
        Map<String, Object> adminCfg = new HashMap<>();
        adminCfg.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());
        try (org.apache.kafka.clients.admin.AdminClient admin =
                     org.apache.kafka.clients.admin.AdminClient.create(adminCfg)) {
            admin.createTopics(TOPICS.stream()
                    .map(t -> new org.apache.kafka.clients.admin.NewTopic(t, 1, (short) 1))
                    .toList()).all().get();
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

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationTemplateRepository templateRepository;
    @Autowired private CommunicationPreferenceRepository preferenceRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.kafka.config.KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM inbox_message");
        // TemplateSeeder (CommandLineRunner) has already seeded the templates on context start.
        assertThat(templateRepository.count()).isGreaterThanOrEqualTo(7);

        // Ensure the real @KafkaListener containers are running (base test profile disables
        // auto-startup) and have been assigned partitions before we publish, so no event is missed.
        listenerRegistry.getListenerContainers().forEach(c -> {
            if (!c.isRunning()) {
                c.start();
            }
        });
        await().atMost(TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
                .allMatch(org.springframework.kafka.listener.MessageListenerContainer::isRunning));
    }

    // ---------------------------------------------------------------------------------------------
    // AC-01: subscription.activated.v1 -> welcome SMS
    // ---------------------------------------------------------------------------------------------
    @Test
    void ac01_subscription_activated_dispatches_welcome_sms() {
        String userId = UUID.randomUUID().toString();
        publish("subscription.events", "subscription.activated.v1", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "customerId", userId,
                "customerName", "Ayse",
                "subscriptionId", "SUB-1001",
                "msisdn", "+905551112233",
                "tariffCode", "GOLD"));

        Notification sent = awaitOne(userId);
        assertThat(sent.getChannel()).isEqualTo("SMS");
        assertThat(sent.getTemplateCode()).isEqualTo("WELCOME");
        assertThat(sent.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(outboxDispatchedCount(userId)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------------------------------
    // AC-02: invoice.generated.v1 -> email
    // ---------------------------------------------------------------------------------------------
    @Test
    void ac02_invoice_generated_dispatches_email() {
        String userId = UUID.randomUUID().toString();
        publish("invoice.events", "invoice.generated.v1", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "customerId", userId,
                "invoiceId", "INV-2001",
                "grandTotal", "149.90",
                "currency", "TRY"));

        Notification sent = awaitOne(userId);
        assertThat(sent.getChannel()).isEqualTo("EMAIL");
        assertThat(sent.getTemplateCode()).isEqualTo("INVOICE_GENERATED");
        assertThat(sent.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(outboxDispatchedCount(userId)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------------------------------
    // AC-03: quota.threshold-reached.v1 and quota.exceeded.v1 -> SMS
    // ---------------------------------------------------------------------------------------------
    @Test
    void ac03_quota_threshold_reached_dispatches_sms() {
        String userId = UUID.randomUUID().toString();
        publish("quota.events", "quota.threshold-reached.v1", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "customerId", userId,
                "subscriptionId", "SUB-3001"));

        Notification sent = awaitOne(userId);
        assertThat(sent.getChannel()).isEqualTo("SMS");
        assertThat(sent.getTemplateCode()).isEqualTo("QUOTA_80_PERCENT");
        assertThat(sent.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(outboxDispatchedCount(userId)).isEqualTo(1L);
    }

    @Test
    void ac03_quota_exceeded_dispatches_sms() {
        String userId = UUID.randomUUID().toString();
        publish("quota.events", "quota.exceeded.v1", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "customerId", userId,
                "subscriptionId", "SUB-3002"));

        Notification sent = awaitOne(userId);
        assertThat(sent.getChannel()).isEqualTo("SMS");
        assertThat(sent.getTemplateCode()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(sent.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(outboxDispatchedCount(userId)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Preference suppression: opted-out user yields SUPPRESSED, no dispatched outbox row.
    // ---------------------------------------------------------------------------------------------
    @Test
    void opted_out_user_is_suppressed_and_no_dispatched_event() {
        String userId = UUID.randomUUID().toString();
        preferenceRepository.save(CommunicationPreference.of(userId, "SMS", false));

        publish("subscription.events", "subscription.activated.v1", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "customerId", userId,
                "customerName", "Mehmet",
                "subscriptionId", "SUB-9001"));

        Notification suppressed = awaitOne(userId);
        assertThat(suppressed.getStatus()).isEqualTo(Notification.STATUS_SUPPRESSED);
        assertThat(suppressed.getSentAt()).isNull();
        // No notification.dispatched.v1 emitted for a suppressed send.
        assertThat(outboxDispatchedCount(userId)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Inbox idempotency: same envelope (same eventId) twice -> exactly one dispatch.
    // ---------------------------------------------------------------------------------------------
    @Test
    void duplicate_event_is_deduplicated_by_inbox() {
        String userId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        Map<String, String> payload = Map.of(
                "eventId", eventId,
                "customerId", userId,
                "customerName", "Fatma",
                "subscriptionId", "SUB-DUP");

        publish("subscription.events", "subscription.activated.v1", payload);
        // First dispatch lands.
        awaitOne(userId);

        // Re-publish the identical envelope (same eventId).
        publish("subscription.events", "subscription.activated.v1", payload);

        // Give the consumer time to (not) process the duplicate; count must stay at exactly one.
        await().during(Duration.ofSeconds(3)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        userId, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                        .isEqualTo(1L));
        assertThat(outboxDispatchedCount(userId)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Publishes a JSON envelope to {@code topic} with the {@code eventType} header (EventRouter shape). */
    private void publish(String topic, String eventType, Map<String, String> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, payload.getOrDefault("customerId", null), json);
            record.headers().add(new RecordHeader("eventType", eventType.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish test event", e);
        }
    }

    /** Awaits exactly one persisted notification for the user and returns it. */
    private Notification awaitOne(String userId) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<Notification> all = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
            assertThat(all).hasSize(1);
        });
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0);
    }

    /** Counts notification.dispatched.v1 outbox rows whose payload carries this userId. */
    private Long outboxDispatchedCount(String userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'notification.dispatched.v1' "
                        + "AND payload->>'userId' = ?",
                Long.class, userId);
    }
}
