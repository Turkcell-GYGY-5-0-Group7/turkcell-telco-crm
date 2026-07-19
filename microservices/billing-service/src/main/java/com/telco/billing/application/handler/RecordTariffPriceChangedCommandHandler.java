package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordTariffPriceChangedCommand;
import com.telco.billing.infrastructure.entity.TariffPrice;
import com.telco.billing.infrastructure.persistence.TariffPriceRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the local {@code tariff_prices} mirror when product-catalog-service reprices a tariff
 * (FR-08). Without this, the mirror is seeded once on first use and the bill-run keeps charging
 * the stale fee forever. Idempotent: re-applying the same event overwrites with identical values.
 */
@Component
public class RecordTariffPriceChangedCommandHandler
        implements CommandHandler<RecordTariffPriceChangedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordTariffPriceChangedCommandHandler.class);

    private final TariffPriceRepository tariffPriceRepository;

    public RecordTariffPriceChangedCommandHandler(TariffPriceRepository tariffPriceRepository) {
        this.tariffPriceRepository = tariffPriceRepository;
    }

    @Override
    @Transactional
    public Void handle(RecordTariffPriceChangedCommand command) {
        TariffPrice price = tariffPriceRepository.findByTariffCode(command.tariffCode())
                .orElse(null);

        if (price == null) {
            price = TariffPrice.of(command.tariffCode(), command.newMonthlyFee(),
                    command.currency(), command.changedAt());
        } else {
            price.update(command.newMonthlyFee(), command.changedAt());
        }
        tariffPriceRepository.save(price);
        LOGGER.info("Refreshed tariff price mirror tariffCode={} newMonthlyFee={}",
                command.tariffCode(), command.newMonthlyFee());
        return null;
    }
}
