package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordSubscriptionSuspendedCommand;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordSubscriptionSuspendedCommandHandler
        implements CommandHandler<RecordSubscriptionSuspendedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordSubscriptionSuspendedCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;

    public RecordSubscriptionSuspendedCommandHandler(SubscriberBillingRecordRepository subscriberRepo) {
        this.subscriberRepo = subscriberRepo;
    }

    @Override
    @Transactional
    public Void handle(RecordSubscriptionSuspendedCommand command) {
        SubscriberBillingRecord record = subscriberRepo
                .findBySubscriptionId(command.subscriptionId())
                .orElse(null);

        if (record == null) {
            LOGGER.warn("No billing record found for suspended subscriptionId={}", command.subscriptionId());
            return null;
        }

        record.suspend(command.suspendedAt());
        subscriberRepo.save(record);
        LOGGER.info("Recorded subscription suspension subscriptionId={}", command.subscriptionId());
        return null;
    }
}
