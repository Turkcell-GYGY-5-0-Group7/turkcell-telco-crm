package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordTariffChangedCommand;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Updates the billing read model after a plan change (Sprint 24 Feature 24.4, design-note D2) so
 * the next bill run charges the new tariff's monthly fee.
 *
 * <p><b>Missing record is TRANSIENT:</b> a billing record exists for every activated subscription,
 * but {@code subscription.activated.v1} and {@code subscription.tariff-changed.v1} arrive on
 * independent consumer groups with no ordering guarantee - a plan change placed immediately after
 * onboarding can be consumed here first. Throwing rolls the transaction (and inbox row) back so
 * Kafka redelivers once the activation consumer has caught up. This mirrors usage-service's
 * transient split on the same race.
 */
@Component
public class RecordTariffChangedCommandHandler
        implements CommandHandler<RecordTariffChangedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordTariffChangedCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;

    public RecordTariffChangedCommandHandler(SubscriberBillingRecordRepository subscriberRepo) {
        this.subscriberRepo = subscriberRepo;
    }

    @Override
    public Void handle(RecordTariffChangedCommand command) {
        SubscriberBillingRecord record = subscriberRepo
                .findBySubscriptionId(command.subscriptionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No billing record for subscription " + command.subscriptionId()
                                + " - subscription.activated.v1 may not be consumed yet; "
                                + "retry on redelivery"));

        String previous = record.getTariffCode();
        record.changeTariff(command.newTariffCode());
        subscriberRepo.save(record);

        LOGGER.info("Billing record tariff changed subscriptionId={} {} -> {}",
                command.subscriptionId(), previous, command.newTariffCode());
        return null;
    }
}
