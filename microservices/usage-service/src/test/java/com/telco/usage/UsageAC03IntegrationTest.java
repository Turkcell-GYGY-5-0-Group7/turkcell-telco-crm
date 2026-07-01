package com.telco.usage;

import com.telco.platform.inbox.InboxService;
import com.telco.platform.outbox.OutboxService;
import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.application.command.MeterCdrCommand;
import com.telco.usage.application.command.ProvisionQuotaCommand;
import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageType;
import com.telco.usage.infrastructure.client.ProductCatalogClient;
import com.telco.usage.infrastructure.client.TariffAllowanceResponse;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-03 Integration Test — Usage Metering (FR-17, FR-18, FR-19, FR-20).
 *
 * <p>Drives the full usage pipeline via the mediator against a real Postgres. Mocks Kafka
 * (OutboxService, InboxService) and ProductCatalogClient (no network) so the test runs in CI
 * without external dependencies. Covers:
 * <ul>
 *   <li>Quota provisioning on subscription activation.</li>
 *   <li>CDR metering with idempotent duplicate detection.</li>
 *   <li>80% threshold event emission when consumption reaches 80% of allowance.</li>
 *   <li>100% exceeded event emission when quota is exhausted.</li>
 *   <li>Overage capture and aggregation via {@code usage.aggregated.v1}.</li>
 * </ul>
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
class UsageAC03IntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @MockitoBean private OutboxService outboxService;
    @MockitoBean private InboxService inboxService;
    @MockitoBean private ProductCatalogClient productCatalogClient;

    @Autowired private Mediator mediator;
    @Autowired private QuotaRepository quotaRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID subscriptionId;
    private UUID customerId;
    private Instant periodStart;
    private Instant periodEnd;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM usage_records");
        jdbc.execute("DELETE FROM quotas");

        subscriptionId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        periodStart = monthStart(Instant.now());
        periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();

        // 100 minutes, 50 SMS, 1000 MB allowances for this subscription.
        when(productCatalogClient.getTariffAllowances(anyString()))
                .thenReturn(new TariffAllowanceResponse(100, 50, 1000));
    }

    @Test
    void quota_is_provisioned_on_subscription_activation() {
        mediator.send(new ProvisionQuotaCommand(
                subscriptionId, customerId, "POSTPAID-S", Instant.now()));

        Instant probe = mid(periodStart);
        Optional<Quota> quota = quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        subscriptionId, probe, probe);

        assertThat(quota).isPresent();
        assertThat(quota.get().getMinutesTotal()).isEqualTo(100L);
        assertThat(quota.get().getSmsTotal()).isEqualTo(50L);
        assertThat(quota.get().getMbTotal()).isEqualTo(1000L);
        assertThat(quota.get().getMinutesRemaining()).isEqualTo(100L);
        assertThat(quota.get().getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void duplicate_activation_does_not_create_second_quota() {
        Instant activatedAt = Instant.now();
        mediator.send(new ProvisionQuotaCommand(subscriptionId, customerId, "POSTPAID-S", activatedAt));
        mediator.send(new ProvisionQuotaCommand(subscriptionId, customerId, "POSTPAID-S", activatedAt));

        long count = quotaRepository.findAll().stream()
                .filter(q -> q.getSubscriptionId().equals(subscriptionId))
                .count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void cdr_metering_decrements_quota_and_publishes_usage_recorded() {
        provision();

        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 200,
                mid(periodStart), "cdr-data-001"));

        Quota updated = activeQuota();
        assertThat(updated.getMbRemaining()).isEqualTo(800L);

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(), eq("usage.recorded.v1"), any());
    }

    @Test
    void duplicate_cdr_is_not_double_counted() {
        provision();
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 100, mid(periodStart), "cdr-dedup-1"));
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 100, mid(periodStart), "cdr-dedup-1"));

        assertThat(activeQuota().getMbRemaining()).isEqualTo(900L);
    }

    @Test
    void threshold_event_fires_once_when_80_percent_consumed() {
        provision();

        // 800 MB out of 1000 = 80% — triggers threshold.
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 800, mid(periodStart), "cdr-thresh-1"));

        verify(outboxService, atLeastOnce()).publish(
                anyString(), anyString(), eq("quota.threshold-reached.v1"), any());
        assertThat(activeQuota().isThresholdNotified()).isTrue();
    }

    @Test
    void exceeded_event_fires_once_when_quota_exhausted() {
        provision();

        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 999, mid(periodStart), "cdr-exc-1"));
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 1, mid(periodStart), "cdr-exc-2"));

        verify(outboxService, atLeastOnce()).publish(
                anyString(), anyString(), eq("quota.exceeded.v1"), any());
        assertThat(activeQuota().isExceededNotified()).isTrue();
        assertThat(activeQuota().getMbRemaining()).isZero();
    }

    @Test
    void overage_is_captured_after_quota_exhaustion() {
        provision();

        // Exhaust all 1000 MB, then consume 200 MB more in overage.
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 1000, mid(periodStart), "cdr-ov-1"));
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 200, mid(periodStart), "cdr-ov-2"));

        // The overage quantity 200 should appear in the aggregate.
        UsageAggregateResponse agg = mediator.send(
                new AggregateUsageCommand(subscriptionId, periodStart, periodEnd));

        assertThat(agg.dataOverageKb()).isEqualTo(200L);
        verify(outboxService, atLeastOnce()).publish(
                anyString(), anyString(), eq("usage.aggregated.v1"), any());
    }

    @Test
    void threshold_event_fires_only_once_per_period() {
        provision();

        // Two CDRs that each push usage past 80% — threshold must fire only once.
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 850, mid(periodStart), "cdr-t2-1"));
        mediator.send(new MeterCdrCommand(subscriptionId, UsageType.DATA, 100, mid(periodStart), "cdr-t2-2"));

        // Verify threshold event was published exactly once.
        verify(outboxService, org.mockito.Mockito.times(1))
                .publish(anyString(), anyString(), eq("quota.threshold-reached.v1"), any());
    }

    @Test
    void no_quota_found_does_not_throw_cdr_is_deferred() {
        UUID unknownSub = UUID.randomUUID();
        // Should not throw; logs a warning and returns gracefully.
        mediator.send(new MeterCdrCommand(unknownSub, UsageType.SMS, 1, mid(periodStart), "cdr-noquota-1"));

        verify(outboxService, never()).publish(anyString(), anyString(), eq("usage.recorded.v1"), any());
    }

    // --- helpers ---

    private void provision() {
        mediator.send(new ProvisionQuotaCommand(
                subscriptionId, customerId, "POSTPAID-S", Instant.now()));
    }

    private Quota activeQuota() {
        Instant probe = mid(periodStart);
        return quotaRepository
                .findBySubscriptionIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThan(
                        subscriptionId, probe, probe)
                .orElseThrow(() -> new AssertionError("No active quota found"));
    }

    private static Instant mid(Instant start) {
        return ZonedDateTime.ofInstant(start, ZoneOffset.UTC).plusDays(15).toInstant();
    }

    private static Instant monthStart(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }
}
