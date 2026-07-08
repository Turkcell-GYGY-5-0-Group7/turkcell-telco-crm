package com.telco.payment.infrastructure.persistence;

import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// @ActiveProfiles("test") keeps this slice off the default profile's Loki appender
// (logback-spring.xml); otherwise its lingering async sender poisons the next context's
// startup with a spurious "Logback configuration error detected" failure.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PaymentRepositoryTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/migration/platform")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void findByOrderId_returns_payment_for_known_order() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.create(orderId, UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        paymentRepository.save(payment);
        flushAndClear();

        assertThat(paymentRepository.findByOrderId(orderId)).isPresent()
                .get().extracting(Payment::getId).isEqualTo(payment.getId());
        assertThat(paymentRepository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByPaymentRequestId_supports_idempotency_lookup() {
        String reqId = "REQ-IDEM-001";
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), reqId);
        paymentRepository.save(payment);
        flushAndClear();

        assertThat(paymentRepository.findByPaymentRequestId(reqId)).isPresent()
                .get().extracting(Payment::getPaymentRequestId).isEqualTo(reqId);
        assertThat(paymentRepository.findByPaymentRequestId("MISSING")).isEmpty();
    }

    @Test
    void findFailedForRetry_returns_failed_payments_within_retry_window() {
        Payment failed = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-RETRY");
        failed.markFailed();
        paymentRepository.save(failed);
        flushAndClear();

        // maxAge is 168 hours ago; a just-created payment is within the window
        Instant maxAge = Instant.now().minusSeconds(168 * 3600);
        List<Payment> retryable = paymentRepository.findFailedForRetry(maxAge, 5);

        assertThat(retryable).anyMatch(p -> p.getId().equals(failed.getId()));
        assertThat(retryable).allMatch(p -> p.getStatus() == PaymentStatus.FAILED);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
