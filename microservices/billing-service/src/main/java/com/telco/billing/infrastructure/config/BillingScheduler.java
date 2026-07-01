package com.telco.billing.infrastructure.config;

import com.telco.billing.application.command.MarkInvoicesOverdueCommand;
import com.telco.platform.mediator.Mediator;
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

    /** Runs nightly at 01:00 UTC to mark past-due invoices as OVERDUE. */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void markOverdueInvoices() {
        LOGGER.info("Overdue invoice check started");
        int count = mediator.send(new MarkInvoicesOverdueCommand());
        LOGGER.info("Overdue invoice check complete marked={}", count);
    }
}
