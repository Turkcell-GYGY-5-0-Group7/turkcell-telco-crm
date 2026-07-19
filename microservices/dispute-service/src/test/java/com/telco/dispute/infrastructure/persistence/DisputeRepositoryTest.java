package com.telco.dispute.infrastructure.persistence;

import com.telco.dispute.domain.Dispute;
import com.telco.dispute.domain.DisputeStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level round-trip test (22.1.3's acceptance criteria). NOT executed live this session -
 * this environment has no Docker available, so Testcontainers cannot start a real PostgreSQL. Written
 * to the same standard as {@code OrderRepositoryTest} and left for the next Docker-available session
 * to run, per this repo's established handling of Docker-gated tests (docs/tasks/lessons.md).
 */
// @ActiveProfiles("test") keeps this slice off the default profile's Loki appender
// (logback-spring.xml); otherwise its lingering async sender poisons the next context's
// startup with a spurious "Logback configuration error detected" failure.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DisputeRepositoryTest {

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
    }

    @Autowired private DisputeRepository disputeRepository;
    @Autowired private DisputeEvidenceRepository disputeEvidenceRepository;
    @Autowired private DisputeStateHistoryRepository disputeStateHistoryRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void save_and_reload_dispute_round_trips_all_fields() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.create(invoiceId, null, customerId, "BILLING_ERROR", new BigDecimal("49.99"));
        disputeRepository.save(dispute);
        flushAndClear();

        Dispute reloaded = disputeRepository.findById(dispute.getId()).orElseThrow();
        assertThat(reloaded.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(reloaded.getPaymentId()).isNull();
        assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
        assertThat(reloaded.getStatus()).isEqualTo(DisputeStatus.OPENED);
        assertThat(reloaded.getReasonCode()).isEqualTo("BILLING_ERROR");
        assertThat(reloaded.getDisputedAmount()).isEqualByComparingTo("49.99");
        assertThat(reloaded.getResolutionAmount()).isNull();
        assertThat(reloaded.getOpenedAt()).isNotNull();
        assertThat(reloaded.getResolvedAt()).isNull();
        assertThat(reloaded.getClosedAt()).isNull();
    }

    @Test
    void create_with_neither_invoice_nor_payment_is_rejected() {
        assertThat(
                org.assertj.core.api.Assertions.catchThrowable(() ->
                        Dispute.create(null, null, UUID.randomUUID(), "OTHER", BigDecimal.TEN)))
                .isInstanceOf(com.telco.platform.common.exception.BusinessRuleException.class);
    }

    @Test
    void evidence_and_state_history_round_trip_via_cascade() {
        Dispute dispute = Dispute.create(null, UUID.randomUUID(), UUID.randomUUID(), "FRAUD", BigDecimal.ONE);
        dispute.addEvidence("customer-1", "dispute-evidence/receipt-001.pdf");
        dispute.beginReview("agent-1");
        disputeRepository.save(dispute);
        flushAndClear();

        Dispute reloaded = disputeRepository.findById(dispute.getId()).orElseThrow();
        assertThat(reloaded.getEvidence()).hasSize(1);
        assertThat(reloaded.getEvidence().get(0).getObjectRef()).isEqualTo("dispute-evidence/receipt-001.pdf");
        assertThat(reloaded.getHistory()).hasSize(1);
        assertThat(reloaded.getHistory().get(0).getFromStatus()).isEqualTo(DisputeStatus.OPENED);
        assertThat(reloaded.getHistory().get(0).getToStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(reloaded.getHistory().get(0).getChangedBy()).isEqualTo("agent-1");

        assertThat(disputeEvidenceRepository.findAll()).hasSize(1);
        assertThat(disputeStateHistoryRepository.findAll()).hasSize(1);
    }

    @Test
    void findByCustomerId_returns_only_disputes_for_that_customer() {
        UUID customer = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        disputeRepository.save(Dispute.create(UUID.randomUUID(), null, customer, "R1", BigDecimal.ONE));
        disputeRepository.save(Dispute.create(UUID.randomUUID(), null, customer, "R2", BigDecimal.TEN));
        disputeRepository.save(Dispute.create(UUID.randomUUID(), null, other, "R3", BigDecimal.ONE));
        flushAndClear();

        Page<Dispute> result = disputeRepository.findByCustomerId(customer, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2)
                .allMatch(d -> d.getCustomerId().equals(customer));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
