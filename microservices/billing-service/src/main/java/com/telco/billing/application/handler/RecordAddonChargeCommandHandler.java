package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordAddonChargeCommand;
import com.telco.billing.infrastructure.entity.AddonCharge;
import com.telco.billing.infrastructure.persistence.AddonChargeRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an addon fee from {@code subscription.addon-attached.v1} for the next bill-run (FR-22).
 * First-write-wins per {@code (orderId, addonCode)}, mirroring {@code RecordOverageCommandHandler}'s
 * idempotency shape: a redelivery that slipped past inbox dedup skips instead of double-charging.
 */
@Component
public class RecordAddonChargeCommandHandler
        implements CommandHandler<RecordAddonChargeCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordAddonChargeCommandHandler.class);

    private final AddonChargeRepository addonChargeRepo;

    public RecordAddonChargeCommandHandler(AddonChargeRepository addonChargeRepo) {
        this.addonChargeRepo = addonChargeRepo;
    }

    @Override
    @Transactional
    public Void handle(RecordAddonChargeCommand command) {
        if (addonChargeRepo.existsByOrderIdAndAddonCode(command.orderId(), command.addonCode())) {
            LOGGER.info("AddonCharge already exists orderId={} addonCode={} - skipping",
                    command.orderId(), command.addonCode());
            return null;
        }

        AddonCharge charge = AddonCharge.of(
                command.subscriptionId(), command.orderId(), command.addonCode(),
                command.addonType(), command.price(), command.currency(), command.attachedAt());
        addonChargeRepo.save(charge);
        LOGGER.info("Recorded addon charge subscriptionId={} addonCode={} price={}",
                command.subscriptionId(), command.addonCode(), command.price());
        return null;
    }
}
