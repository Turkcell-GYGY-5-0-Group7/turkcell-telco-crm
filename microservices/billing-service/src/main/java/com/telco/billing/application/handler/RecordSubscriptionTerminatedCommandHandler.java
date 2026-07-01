package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordSubscriptionTerminatedCommand;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordSubscriptionTerminatedCommandHandler
        implements CommandHandler<RecordSubscriptionTerminatedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordSubscriptionTerminatedCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;

    public RecordSubscriptionTerminatedCommandHandler(SubscriberBillingRecordRepository subscriberRepo) {
        this.subscriberRepo = subscriberRepo;
    }

    @Override
    @Transactional
    public Void handle(RecordSubscriptionTerminatedCommand command) {
        SubscriberBillingRecord record = subscriberRepo
                .findBySubscriptionId(command.subscriptionId())
                .orElse(null);

        if (record == null) {
            LOGGER.warn("No billing record found for terminated subscriptionId={}", command.subscriptionId());
            return null;
        }

        record.terminate(command.terminatedAt());
        subscriberRepo.save(record);
        LOGGER.info("Recorded subscription termination subscriptionId={}", command.subscriptionId());
        return null;
    }
}
