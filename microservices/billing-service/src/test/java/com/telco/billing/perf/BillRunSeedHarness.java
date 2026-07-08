package com.telco.billing.perf;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Repeatable seed harness for bill-run throughput validation (NFR-02, feature 14.3.2).
 *
 * <p>Populates exactly the read models {@code RunBillCommandHandler} / {@code BillRunBatchProcessor}
 * consume: {@code subscriber_billing_records} (ACTIVE postpaid subscribers), {@code tariff_prices}
 * (the cached pricing a real bill-run reads with no synchronous cross-service call — see
 * {@code RecordSubscriptionActivatedCommandHandler}), and a realistic slice of
 * {@code overage_records} (usage-based overage lines), so a seeded bill-run exercises the same code
 * paths — tariff line + optional data/voice/SMS overage lines — as production traffic fed one
 * subscriber at a time through Kafka.
 *
 * <p>Seeding goes directly to JDBC (not through the mediator/one-command-per-subscriber) because
 * this is test-fixture setup, not the bill-run itself: driving 100K individual
 * {@code RecordSubscriptionActivatedCommand}s through the mediator would dominate the harness's own
 * runtime and is not what NFR-02 measures. The bill-run under test (the thing being timed) still
 * goes through {@code mediator.send(new RunBillCommand(...))} exactly as production does — this
 * class never touches the {@code invoices}/{@code invoice_lines}/outbox tables.
 */
public final class BillRunSeedHarness {

    /** Small, realistic tariff catalog so the bill-run exercises more than one price/currency. */
    public static final List<String> TARIFF_CODES =
            List.of("POSTPAID-S", "POSTPAID-M", "POSTPAID-L", "POSTPAID-XL", "POSTPAID-BIZ");

    private static final int JDBC_BATCH_CHUNK = 2000;

    private BillRunSeedHarness() {
    }

    /** Result summary of a seed run, for logging/reporting. */
    public record SeedResult(int subscribers, int tariffCodes, int overageRecords) {}

    /**
     * Seeds {@code subscriberCount} ACTIVE postpaid subscribers spread across {@link #TARIFF_CODES},
     * one cached {@code tariff_prices} row per tariff code, and an overage record for
     * {@code overageFraction} of subscribers for the given billing period.
     *
     * @param jdbc            JDBC template pointed at the billing-service schema
     * @param subscriberCount how many ACTIVE subscribers to create
     * @param periodStart     the billing period start the bill-run under test will use
     * @param periodEnd       the billing period end the bill-run under test will use
     * @param overageFraction fraction (0.0-1.0) of subscribers that get a usage-overage record for
     *                        this period, exercising the overage invoice-line branch
     * @param seed            RNG seed, for reproducible tariff/overage assignment across runs
     */
    public static SeedResult seed(JdbcTemplate jdbc, int subscriberCount,
                                   Instant periodStart, Instant periodEnd,
                                   double overageFraction, long seed) {
        Instant activatedAt = periodStart.minus(Duration.ofDays(60));
        Instant now = Instant.now();
        Random random = new Random(seed);

        seedTariffPrices(jdbc, now);
        int overageRecords = seedSubscribersAndOverage(
                jdbc, subscriberCount, activatedAt, periodStart, periodEnd, overageFraction, random);

        return new SeedResult(subscriberCount, TARIFF_CODES.size(), overageRecords);
    }

    private static void seedTariffPrices(JdbcTemplate jdbc, Instant effectiveFrom) {
        String sql = """
                INSERT INTO tariff_prices (id, tariff_code, monthly_fee, currency, effective_from, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tariff_code) DO NOTHING
                """;
        List<Object[]> batch = new ArrayList<>();
        BigDecimal baseFee = new BigDecimal("49.99");
        Timestamp effectiveFromTs = Timestamp.from(effectiveFrom);
        for (int i = 0; i < TARIFF_CODES.size(); i++) {
            BigDecimal monthlyFee = baseFee.add(BigDecimal.valueOf(i * 25L));
            batch.add(new Object[]{
                    UUID.randomUUID(), TARIFF_CODES.get(i), monthlyFee, "TRY", effectiveFromTs, effectiveFromTs
            });
        }
        jdbc.batchUpdate(sql, batch);
    }

    private static int seedSubscribersAndOverage(JdbcTemplate jdbc, int subscriberCount,
                                                  Instant activatedAt, Instant periodStart, Instant periodEnd,
                                                  double overageFraction, Random random) {
        String subscriberSql = """
                INSERT INTO subscriber_billing_records
                    (id, subscription_id, customer_id, tariff_code, status, activated_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """;
        String overageSql = """
                INSERT INTO overage_records
                    (id, subscription_id, period_start, period_end, voice_overage_seconds,
                     sms_overage_count, data_overage_kb, aggregated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        List<Object[]> subscriberBatch = new ArrayList<>(JDBC_BATCH_CHUNK);
        List<Object[]> overageBatch = new ArrayList<>(JDBC_BATCH_CHUNK);
        int overageCount = 0;
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp activatedAtTs = Timestamp.from(activatedAt);
        Timestamp periodStartTs = Timestamp.from(periodStart);
        Timestamp periodEndTs = Timestamp.from(periodEnd);

        for (int i = 0; i < subscriberCount; i++) {
            UUID subscriptionId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            String tariffCode = TARIFF_CODES.get(i % TARIFF_CODES.size());

            subscriberBatch.add(new Object[]{
                    UUID.randomUUID(), subscriptionId, customerId, tariffCode, activatedAtTs, now
            });

            if (random.nextDouble() < overageFraction) {
                overageBatch.add(new Object[]{
                        UUID.randomUUID(), subscriptionId, periodStartTs, periodEndTs,
                        (long) random.nextInt(3600),   // up to 1h voice overage
                        (long) random.nextInt(200),    // up to 200 SMS overage
                        (long) random.nextInt(5_000_000), // up to ~5GB overage in KB
                        now, now
                });
                overageCount++;
            }

            if (subscriberBatch.size() == JDBC_BATCH_CHUNK) {
                jdbc.batchUpdate(subscriberSql, subscriberBatch);
                subscriberBatch.clear();
            }
            if (overageBatch.size() == JDBC_BATCH_CHUNK) {
                jdbc.batchUpdate(overageSql, overageBatch);
                overageBatch.clear();
            }
        }

        if (!subscriberBatch.isEmpty()) {
            jdbc.batchUpdate(subscriberSql, subscriberBatch);
        }
        if (!overageBatch.isEmpty()) {
            jdbc.batchUpdate(overageSql, overageBatch);
        }

        return overageCount;
    }
}
