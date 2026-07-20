package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordTariffChangedCommand;
import com.telco.billing.infrastructure.client.ProductCatalogBillingClient;
import com.telco.billing.infrastructure.client.TariffPricingResponse;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.entity.TariffPrice;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.billing.infrastructure.persistence.TariffPriceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Updates the billing read model after a plan change (Sprint 24 Feature 24.4, design-note D2) so
 * the next bill run charges the new tariff's monthly fee.
 *
 * <p><b>The new tariff's price is cached exactly like activation caches it</b> (bug found via the
 * Sprint 24.8 live E2E, 2026-07-20): {@code tariff_prices} is populated lazily by
 * {@link RecordSubscriptionActivatedCommandHandler} for the ACTIVATION tariff only, so after a
 * plan change to a tariff no subscriber ever activated on, the bill run threw
 * {@code "No tariff price cached"} for the subscriber and generated NO invoice at all. The same
 * lazy fetch-and-cache (fail-soft on a missing catalog entry) now runs here for the new code.
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
    private final TariffPriceRepository tariffPriceRepo;
    private final ProductCatalogBillingClient catalogClient;

    public RecordTariffChangedCommandHandler(SubscriberBillingRecordRepository subscriberRepo,
                                             TariffPriceRepository tariffPriceRepo,
                                             ProductCatalogBillingClient catalogClient) {
        this.subscriberRepo = subscriberRepo;
        this.tariffPriceRepo = tariffPriceRepo;
        this.catalogClient = catalogClient;
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

        // Cache the new tariff's price locally so the bill run needs no synchronous call
        // (mirrors RecordSubscriptionActivatedCommandHandler).
        if (tariffPriceRepo.findByTariffCode(command.newTariffCode()).isEmpty()) {
            try {
                TariffPricingResponse pricing = catalogClient.getTariffPricing(command.newTariffCode());
                TariffPrice tp = TariffPrice.of(
                        pricing.code() != null ? pricing.code() : command.newTariffCode(),
                        pricing.monthlyFee(),
                        pricing.currency() != null ? pricing.currency() : "TRY",
                        Instant.now());
                tariffPriceRepo.save(tp);
                LOGGER.info("Cached tariff price tariffCode={} fee={}",
                        command.newTariffCode(), pricing.monthlyFee());
            } catch (ResourceNotFoundException e) {
                LOGGER.warn("Tariff {} not found in catalog - price not cached",
                        command.newTariffCode());
            }
        }

        LOGGER.info("Billing record tariff changed subscriptionId={} {} -> {}",
                command.subscriptionId(), previous, command.newTariffCode());
        return null;
    }
}
