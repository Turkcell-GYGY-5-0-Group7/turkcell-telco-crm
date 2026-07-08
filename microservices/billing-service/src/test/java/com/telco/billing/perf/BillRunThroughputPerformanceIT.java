package com.telco.billing.perf;

import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.platform.mediator.Mediator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-02 / feature 14.3.2 — bill-run throughput validation.
 *
 * <p>Seeds a configurable number of ACTIVE postpaid subscribers via {@link BillRunSeedHarness} and
 * runs a single, real bill-run through the mediator exactly as production does
 * ({@code mediator.send(new RunBillCommand(...))}), measuring wall-clock time and asserting no
 * duplicate invoices are produced.
 *
 * <p>Deliberately named {@code *IT} (not {@code *Test}) so it is excluded from the default Surefire
 * {@code mvn test}/{@code mvn verify} run in this module (no Failsafe binding here, unlike
 * {@code acceptance-tests}) — a 100K-subscriber run is a multi-minute performance validation, not a
 * unit/fast-integration test, and must not slow down every routine build. Run explicitly:
 *
 * <pre>
 * mvn -f microservices/pom.xml -pl billing-service -am test \
 *     -Dtest=BillRunThroughputPerformanceIT -DfailIfNoTests=false -Dschema.registry.skip=true \
 *     -DsubscriberCount=100000 -DbatchSize=500 -Dparallelism=8
 * </pre>
 *
 * <p>{@code subscriberCount}/{@code batchSize}/{@code parallelism} are all overridable via system
 * properties so the same class serves both small-scale tuning runs and the full 100K validation
 * without a code change.
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
class BillRunThroughputPerformanceIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillRunThroughputPerformanceIT.class);

    private static final int SUBSCRIBER_COUNT =
            Integer.getInteger("subscriberCount", 100_000);
    private static final int BATCH_SIZE =
            Integer.getInteger("batchSize", 500);
    private static final int PARALLELISM =
            Integer.getInteger("parallelism", 8);
    private static final double OVERAGE_FRACTION = 0.2;
    private static final Duration TARGET = Duration.ofMinutes(30);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Sized for `parallelism` concurrent batch transactions + the one idle connection the
        // mediator's own outer transaction holds for the whole RunBillCommand call + headroom.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> PARALLELISM + 6);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.jdbc.batch_size", () -> "50");
        registry.add("spring.jpa.properties.hibernate.order_inserts", () -> "true");
        registry.add("spring.jpa.properties.hibernate.order_updates", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
        registry.add("telco.billing.bill-run.batch-size", () -> BATCH_SIZE);
        registry.add("telco.billing.bill-run.parallelism", () -> PARALLELISM);
    }

    @Autowired private Mediator mediator;
    @Autowired private JdbcTemplate jdbc;

    private static Instant periodStart;
    private static Instant periodEnd;

    @BeforeAll
    static void computePeriod() {
        periodStart = ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
        periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    @AfterAll
    static void report() {
        LOGGER.info("Bill-run throughput IT finished — see assertions/logs above for elapsed time.");
    }

    @Test
    void bill_run_processes_seeded_subscribers_within_target_with_no_duplicate_invoices() {
        LOGGER.info("Seeding {} ACTIVE subscribers (batchSize={}, parallelism={})...",
                SUBSCRIBER_COUNT, BATCH_SIZE, PARALLELISM);
        Instant seedStart = Instant.now();
        BillRunSeedHarness.SeedResult seedResult = BillRunSeedHarness.seed(
                jdbc, SUBSCRIBER_COUNT, periodStart, periodEnd, OVERAGE_FRACTION, 42L);
        Duration seedElapsed = Duration.between(seedStart, Instant.now());
        LOGGER.info("Seed complete: subscribers={} tariffCodes={} overageRecords={} in {}",
                seedResult.subscribers(), seedResult.tariffCodes(), seedResult.overageRecords(), seedElapsed);

        Instant runStart = Instant.now();
        RunBillResult result = mediator.send(new RunBillCommand(periodStart, periodEnd));
        Duration elapsed = Duration.between(runStart, Instant.now());

        double invoicesPerSecond = elapsed.toMillis() == 0
                ? 0
                : result.invoicesGenerated() * 1000.0 / elapsed.toMillis();

        LOGGER.info("BILL-RUN THROUGHPUT RESULT: subscribers={} generated={} skipped={} "
                        + "elapsed={} ({} ms) target={} throughput={}/s batchSize={} parallelism={}",
                SUBSCRIBER_COUNT, result.invoicesGenerated(), result.invoicesSkipped(),
                elapsed, elapsed.toMillis(), TARGET, String.format("%.1f", invoicesPerSecond),
                BATCH_SIZE, PARALLELISM);

        assertThat(result.invoicesGenerated())
                .as("every seeded subscriber should get exactly one invoice")
                .isEqualTo(SUBSCRIBER_COUNT);
        assertThat(result.invoicesSkipped())
                .as("no subscriber should be skipped on a first run for this period")
                .isZero();

        assertThat(elapsed)
                .as("bill-run for %d subscribers must complete within %s (NFR-02)", SUBSCRIBER_COUNT, TARGET)
                .isLessThan(TARGET);

        assertNoDuplicateInvoices();
        assertInvoiceCountMatches();
    }

    /** Direct SQL check (not just the application-level counters) — the actual NFR-02 assertion. */
    private void assertNoDuplicateInvoices() {
        List<Map<String, Object>> duplicates = jdbc.queryForList("""
                SELECT subscription_id, period_start, COUNT(*) AS cnt
                FROM invoices
                GROUP BY subscription_id, period_start
                HAVING COUNT(*) > 1
                """);
        assertThat(duplicates)
                .as("no (subscription_id, period_start) pair should have more than one invoice")
                .isEmpty();
    }

    private void assertInvoiceCountMatches() {
        Integer invoiceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE period_start = ?", Integer.class,
                java.sql.Timestamp.from(periodStart));
        assertThat(invoiceCount).isEqualTo(SUBSCRIBER_COUNT);
    }
}
