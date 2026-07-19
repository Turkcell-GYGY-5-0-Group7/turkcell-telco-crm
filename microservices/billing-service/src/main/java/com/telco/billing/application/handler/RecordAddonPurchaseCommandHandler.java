package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordAddonPurchaseCommand;
import com.telco.billing.infrastructure.entity.AddonChargeRecord;
import com.telco.billing.infrastructure.persistence.AddonChargeRecordRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists one unbilled {@link AddonChargeRecord} per addon purchase (Sprint 24 Feature 24.3,
 * design-note D3). The next bill run turns each unbilled row into exactly one invoice line and
 * flips {@code billed} in the bill-run transaction. Redelivery dedup is the mediator
 * {@code InboxBehavior} (the command is an {@code IdempotentRequest} keyed on the order-item
 * message id), atomic with this insert.
 */
@Component
public class RecordAddonPurchaseCommandHandler
        implements CommandHandler<RecordAddonPurchaseCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordAddonPurchaseCommandHandler.class);

    private final AddonChargeRecordRepository addonChargeRecordRepository;

    public RecordAddonPurchaseCommandHandler(AddonChargeRecordRepository addonChargeRecordRepository) {
        this.addonChargeRecordRepository = addonChargeRecordRepository;
    }

    @Override
    public Void handle(RecordAddonPurchaseCommand command) {
        AddonChargeRecord charge = AddonChargeRecord.purchased(
                command.subscriptionId(),
                command.customerId(),
                command.addonCode(),
                command.addonName(),
                command.price(),
                command.currency(),
                command.purchasedAt());
        addonChargeRecordRepository.save(charge);

        LOGGER.info("Addon charge recorded subscriptionId={} addon={} price={} {}",
                command.subscriptionId(), command.addonCode(), command.price(), command.currency());
        return null;
    }
}
