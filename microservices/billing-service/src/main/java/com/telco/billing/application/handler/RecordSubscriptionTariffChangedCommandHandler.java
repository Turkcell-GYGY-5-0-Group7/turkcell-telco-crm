package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordSubscriptionTariffChangedCommand;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@code subscription.tariff-changed.v1} (FR-09) to the local billing record so the next
 * bill-run prices the new tariff. Idempotent: reapplying the same code is a no-op overwrite.
 */
@Component
public class RecordSubscriptionTariffChangedCommandHandler
        implements CommandHandler<RecordSubscriptionTariffChangedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordSubscriptionTariffChangedCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;

    public RecordSubscriptionTariffChangedCommandHandler(SubscriberBillingRecordRepository subscriberRepo) {
        this.subscriberRepo = subscriberRepo;
    }

    @Override
    @Transactional
    public Void handle(RecordSubscriptionTariffChangedCommand command) {
        SubscriberBillingRecord record = subscriberRepo
                .findBySubscriptionId(command.subscriptionId())
                .orElse(null);

        if (record == null) {
            LOGGER.warn("No billing record for tariff-changed subscriptionId={}", command.subscriptionId());
            return null;
        }

        record.changeTariff(command.newTariffCode());
        subscriberRepo.save(record);
        LOGGER.info("Recorded tariff change subscriptionId={} newTariffCode={}",
                command.subscriptionId(), command.newTariffCode());
        return null;
    }
}
