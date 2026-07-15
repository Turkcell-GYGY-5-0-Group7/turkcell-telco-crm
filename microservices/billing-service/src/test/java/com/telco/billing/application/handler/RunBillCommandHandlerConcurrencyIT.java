package com.telco.billing.application.handler;

import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.billing.perf.BillRunSeedHarness;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves exactly one billing-service pod owns a given period's bill-run under concurrent invocation
 * (feature 17.4.2, Sprint 17 README exit criteria), against real Testcontainers Postgres + Redis -
 * not a mocked {@code DistributedLock}.
 *
 * <p>Deliberately named {@code *IT} (not {@code *Test}), matching {@code BillRunThroughputPerformanceIT}'s
 * convention in this module (no Failsafe binding here) - this needs Docker for both containers and
 * must not run in the default {@code mvn test}.
 *
 * <p>Uses {@code batch-size=1}/{@code parallelism=1} and a modest subscriber count so the winning
 * invocation's real work (PDF render + storage + DB writes, sequential) reliably takes longer than
 * the short {@code telco.platform.lock.wait-time} configured here, giving the losing invocation a
 * genuine, deterministic chance to observe the lock as held rather than racing on timing.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        })
@ActiveProfiles("test")
@Testcontainers
class RunBillCommandHandlerConcurrencyIT extends RedisContainerSupport {

    private static final int SUBSCRIBER_COUNT = 100;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
        registry.add("telco.billing.bill-run.batch-size", () -> 1);
        registry.add("telco.billing.bill-run.parallelism", () -> 1);
        registry.add("telco.platform.lock.enabled", () -> true);
        registry.add("telco.platform.lock.redis.address", RunBillCommandHandlerConcurrencyIT::redisAddress);
        registry.add("telco.platform.lock.wait-time", () -> "2s");
    }

    @Autowired
    private Mediator mediator;
    @Autowired
    private JdbcTemplate jdbc;

    private static Instant periodStart;
    private static Instant periodEnd;

    @BeforeAll
    static void computePeriod() {
        periodStart = ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
        periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    @Test
    void exactlyOnePodOwnsTheRunForAGivenPeriod() throws Exception {
        BillRunSeedHarness.seed(jdbc, SUBSCRIBER_COUNT, periodStart, periodEnd, 0.0, 7L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startTogether = new CyclicBarrier(2);
        Callable<RunBillResult> invocation = () -> {
            startTogether.await();
            return mediator.send(new RunBillCommand(periodStart, periodEnd));
        };
        try {
            Future<RunBillResult> first = executor.submit(invocation);
            Future<RunBillResult> second = executor.submit(invocation);
            RunBillResult resultA = first.get(60, TimeUnit.SECONDS);
            RunBillResult resultB = second.get(60, TimeUnit.SECONDS);

            List<RunBillResult> results = List.of(resultA, resultB);
            long winners = results.stream().filter(r -> !r.runAlreadyOwned()).count();
            long losers = results.stream().filter(RunBillResult::runAlreadyOwned).count();

            assertThat(winners).as("exactly one invocation should own the run").isEqualTo(1);
            assertThat(losers).as("the other invocation should observe the lock as held").isEqualTo(1);

            RunBillResult winner = results.stream().filter(r -> !r.runAlreadyOwned()).findFirst().orElseThrow();
            RunBillResult loser = results.stream().filter(RunBillResult::runAlreadyOwned).findFirst().orElseThrow();
            assertThat(winner.invoicesGenerated()).isEqualTo(SUBSCRIBER_COUNT);
            assertThat(loser.invoicesGenerated()).isZero();
            assertThat(loser.invoicesSkipped()).isZero();
        } finally {
            executor.shutdown();
        }

        assertNoDuplicateInvoices();
        assertInvoiceCountMatches();
    }

    /** Direct SQL check (not just the application-level counters), mirroring BillRunThroughputPerformanceIT. */
    private void assertNoDuplicateInvoices() {
        List<Map<String, Object>> duplicates = jdbc.queryForList("""
                SELECT subscription_id, period_start, COUNT(*) AS cnt
                FROM invoices
                GROUP BY subscription_id, period_start
                HAVING COUNT(*) > 1
                """);
        assertThat(duplicates).isEmpty();
    }

    private void assertInvoiceCountMatches() {
        Integer invoiceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE period_start = ?", Integer.class,
                java.sql.Timestamp.from(periodStart));
        assertThat(invoiceCount).isEqualTo(SUBSCRIBER_COUNT);
    }
}
