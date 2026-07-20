package com.telco.usage.infrastructure.config;

import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.infrastructure.persistence.UsageRecordRepository;
import com.telco.platform.mediator.Mediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled monthly overage aggregation (FR-20). On the 1st at 01:30 UTC - before billing's
 * 02:00 bill-run - emits {@code usage.aggregated.v1} for every subscription with overage in the
 * previous calendar month, reusing the same {@link AggregateUsageCommand} the admin endpoint
 * dispatches. {@code @EnableScheduling} lives here because usage-service had no scheduled task
 * before this (payment-service's SchedulerConfig precedent).
 *
 * <p>No distributed lock: duplicate emission (multi-replica or an admin re-trigger) is harmless
 * because billing's {@code RecordOverageCommandHandler} is first-write-wins per
 * {@code (subscriptionId, periodStart)}. Per-subscription failures are logged and skipped so one
 * bad row cannot starve the rest of the population.
 */
@Component
@EnableScheduling
public class UsageAggregationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsageAggregationScheduler.class);

    private final Mediator mediator;
    private final UsageRecordRepository usageRecordRepository;

    public UsageAggregationScheduler(Mediator mediator, UsageRecordRepository usageRecordRepository) {
        this.mediator = mediator;
        this.usageRecordRepository = usageRecordRepository;
    }

    @Scheduled(cron = "0 30 1 1 * *", zone = "UTC")
    public void aggregatePreviousMonth() {
        YearMonth previous = YearMonth.from(LocalDate.now(ZoneOffset.UTC)).minusMonths(1);
        Instant periodStart = previous.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = previous.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<UUID> subscriptionIds =
                usageRecordRepository.findSubscriptionIdsWithOverage(periodStart, periodEnd);
        LOGGER.info("Scheduled usage aggregation started period={} subscriptions={}",
                previous, subscriptionIds.size());

        int failed = 0;
        for (UUID subscriptionId : subscriptionIds) {
            try {
                mediator.send(new AggregateUsageCommand(subscriptionId, periodStart, periodEnd));
            } catch (Exception e) {
                failed++;
                LOGGER.error("Scheduled usage aggregation failed subscriptionId={} period={}",
                        subscriptionId, previous, e);
            }
        }
        LOGGER.info("Scheduled usage aggregation complete period={} emitted={} failed={}",
                previous, subscriptionIds.size() - failed, failed);
    }
}
