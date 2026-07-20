package com.telco.billing.infrastructure.config;

import com.telco.billing.application.command.MarkInvoicesOverdueCommand;
import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.platform.mediator.Mediator;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingScheduler.class);

    private final Mediator mediator;

    public BillingScheduler(Mediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Monthly bill-run (FR-21): on the 1st at 02:00 UTC, invoices the previous calendar month.
     * Safe to fire on every replica: {@code RunBillCommandHandler} holds a period-keyed
     * {@code DistributedLock}, so exactly one pod owns a given period's run; the handler is also
     * idempotent per period, so a rerun (e.g. after the admin already triggered the same period via
     * {@code POST /api/v1/billing/runs}) skips already-invoiced subscribers rather than duplicating.
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "UTC")
    public void runMonthlyBillRun() {
        YearMonth previous = YearMonth.from(LocalDate.now(ZoneOffset.UTC)).minusMonths(1);
        Instant periodStart = previous.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = previous.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        LOGGER.info("Scheduled monthly bill-run started period={} [{} .. {})", previous, periodStart, periodEnd);
        RunBillResult result = mediator.send(new RunBillCommand(periodStart, periodEnd));
        LOGGER.info("Scheduled monthly bill-run complete period={} result={}", previous, result);
    }

    /** Runs nightly at 01:00 UTC to mark past-due invoices as OVERDUE. */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void markOverdueInvoices() {
        LOGGER.info("Overdue invoice check started");
        int count = mediator.send(new MarkInvoicesOverdueCommand());
        LOGGER.info("Overdue invoice check complete marked={}", count);
    }
}
