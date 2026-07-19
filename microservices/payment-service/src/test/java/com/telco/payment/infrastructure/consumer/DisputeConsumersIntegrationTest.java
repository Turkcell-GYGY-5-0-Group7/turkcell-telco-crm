package com.telco.payment.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.payment.application.consumer.DisputeOpenedPaymentConsumer;
import com.telco.payment.application.consumer.DisputeResolvedCustomerPaymentConsumer;
import com.telco.payment.application.consumer.DisputeResolvedMerchantPaymentConsumer;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real cross-service integration test for the three dispute-service inbox consumers
 * (Sprint 22 Feature 22.6.3, ADR-028 Section 5) - {@link DisputeOpenedPaymentConsumer},
 * {@link DisputeResolvedMerchantPaymentConsumer}, {@link DisputeResolvedCustomerPaymentConsumer}.
 *
 * <p><b>NOT run this session - needs Docker (Postgres + Kafka Testcontainers).</b> Written to the
 * same standard as identity-service's {@code CustomerRegisteredEventConsumerIntegrationTest} and
 * billing-service's sibling {@code DisputeConsumersIntegrationTest} - drives the ACTUAL consumers by
 * publishing real JSON envelopes to the real {@code dispute.events} topic with the {@code eventType}
 * header, since dispute-service is not part of this Spring context.
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
class DisputeConsumersIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Container
    static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer("apache/kafka:4.0.0");

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TOPIC = "dispute.events";
    private static final String EVENT_TYPE_HEADER = "eventType";

    private static Producer<String, String> producer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired PaymentRepository paymentRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeAll
    static void startProducer() throws Exception {
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
        jdbc.execute("DELETE FROM payment_attempts");
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM outbox_event");

        listenerRegistry.getListenerContainers().forEach(c -> {
            if (!c.isRunning()) {
                c.start();
            }
        });
        await().atMost(TIMEOUT).until(() -> listenerRegistry.getListenerContainers().stream()
                .allMatch(MessageListenerContainer::isRunning));
    }

    private Payment newCompletedPayment() {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), "REQ-" + UUID.randomUUID());
        payment.markCompleted();
        return paymentRepository.save(payment);
    }

    @Test
    void disputeOpened_marks_payment_disputed_with_no_psp_call_or_status_change() {
        Payment payment = newCompletedPayment();

        publish("dispute.opened.v1", Map.of(
                "disputeId", UUID.randomUUID().toString(),
                "paymentId", payment.getId().toString(),
                "customerId", payment.getCustomerId().toString(),
                "disputedAmount", new BigDecimal("49.99"),
                "reasonCode", "PSP_CHARGEBACK",
                "openedAt", Instant.now().toString()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(reloaded.isDisputed()).isTrue();
            assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });
    }

    @Test
    void disputeResolvedMerchant_clears_disputed_flag_with_no_status_change() {
        Payment payment = newCompletedPayment();
        payment.markDisputed();
        paymentRepository.save(payment);

        publish("dispute.resolved-merchant.v1", Map.of(
                "disputeId", UUID.randomUUID().toString(),
                "paymentId", payment.getId().toString(),
                "resolvedAt", Instant.now().toString()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(reloaded.isDisputed()).isFalse();
            assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });
    }

    @Test
    void disputeResolvedCustomer_on_completed_payment_triggers_real_refund() {
        Payment payment = newCompletedPayment();
        payment.markDisputed();
        paymentRepository.save(payment);

        publish("dispute.resolved-customer.v1", Map.of(
                "disputeId", UUID.randomUUID().toString(),
                "paymentId", payment.getId().toString(),
                "resolutionAmount", new BigDecimal("49.99"),
                "resolvedAt", Instant.now().toString()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });
    }

    private void publish(String eventType, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), json);
            record.headers().add(new RecordHeader(EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish test event", e);
        }
    }
}
