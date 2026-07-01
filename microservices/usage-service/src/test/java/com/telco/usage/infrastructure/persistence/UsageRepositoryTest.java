package com.telco.usage.infrastructure.persistence;

import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageRecord;
import com.telco.usage.domain.UsageType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsageRepositoryTest {

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

    @Autowired private QuotaRepository quotaRepository;
    @Autowired private UsageRecordRepository usageRecordRepository;
    @Autowired private TestEntityManager entityManager;

    // Helpers using the actual Quota.create() and UsageRecord.create() APIs

    private Quota saveQuota(UUID subscriptionId, Instant start, Instant end) {
        Quota quota = Quota.create(subscriptionId, null, start, end, 500L, 200L, 1024L);
        return quotaRepository.save(quota);
    }

    private UsageRecord saveRecord(UUID subscriptionId, UUID quotaId, String cdrRef,
                                   UsageType type, long qty, boolean overage) {
        UsageRecord record = UsageRecord.create(subscriptionId, quotaId, type, qty, overage, cdrRef);
        return usageRecordRepository.save(record);
    }

    @Test
    void findFirstBySubscriptionId_returns_quota_for_subscription() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(29, ChronoUnit.DAYS);
        Quota quota = saveQuota(subscriptionId, start, end);
        flushAndClear();

        Optional<Quota> found = quotaRepository.findFirstBySubscriptionId(subscriptionId);

        assertThat(found).isPresent()
                .get().extracting(Quota::getSubscriptionId).isEqualTo(subscriptionId);
        assertThat(quotaRepository.findFirstBySubscriptionId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findBySubscriptionIdAndPeriodContaining_returns_active_quota_inside_window() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-30T23:59:59Z");
        saveQuota(subscriptionId, start, end);
        flushAndClear();

        Instant queryAt = Instant.parse("2026-06-15T12:00:00Z");
        Optional<Quota> found = quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        subscriptionId, queryAt, queryAt);

        assertThat(found).isPresent()
                .get().extracting(Quota::getSubscriptionId).isEqualTo(subscriptionId);
    }

    @Test
    void findBySubscriptionIdAndPeriodContaining_returns_empty_outside_window() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-30T23:59:59Z");
        saveQuota(subscriptionId, start, end);
        flushAndClear();

        Instant outside = Instant.parse("2026-07-15T00:00:00Z");
        Optional<Quota> found = quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        subscriptionId, outside, outside);

        assertThat(found).isEmpty();
    }

    @Test
    void existsByCdrRef_true_for_known_ref_false_for_unknown() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(29, ChronoUnit.DAYS);
        Quota quota = saveQuota(subscriptionId, start, end);

        String cdrRef = "CDR-" + UUID.randomUUID();
        saveRecord(subscriptionId, quota.getId(), cdrRef, UsageType.DATA, 100L, false);
        flushAndClear();

        assertThat(usageRecordRepository.existsByCdrRef(cdrRef)).isTrue();
        assertThat(usageRecordRepository.existsByCdrRef("MISSING")).isFalse();
    }

    @Test
    void findBySubscriptionIdAndRecordedAtBetween_returns_records_in_window() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(29, ChronoUnit.DAYS);
        Quota quota = saveQuota(subscriptionId, start, end);

        saveRecord(subscriptionId, quota.getId(), "CDR-HIST-1", UsageType.VOICE, 60L, false);
        flushAndClear();

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        Page<UsageRecord> results = usageRecordRepository
                .findBySubscriptionIdAndRecordedAtBetween(subscriptionId, from, to,
                        PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getCdrRef()).isEqualTo("CDR-HIST-1");
    }

    @Test
    void sumOverageBySubscriptionAndType_returns_total_overage_quantity() {
        UUID subscriptionId = UUID.randomUUID();
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(29, ChronoUnit.DAYS);
        Quota quota = saveQuota(subscriptionId, start, end);

        saveRecord(subscriptionId, quota.getId(), "CDR-OVR-1", UsageType.DATA, 50L, true);
        saveRecord(subscriptionId, quota.getId(), "CDR-OVR-2", UsageType.DATA, 30L, true);
        saveRecord(subscriptionId, quota.getId(), "CDR-NORM-1", UsageType.DATA, 20L, false);
        flushAndClear();

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        Long total = usageRecordRepository.sumOverageBySubscriptionAndType(
                subscriptionId, UsageType.DATA, from, to);

        assertThat(total).isEqualTo(80L);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
