package com.telco.billing.application.handler;

import com.telco.billing.application.command.RecordSubscriptionActivatedCommand;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class RecordSubscriptionActivatedCommandHandler
        implements CommandHandler<RecordSubscriptionActivatedCommand, Void> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecordSubscriptionActivatedCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;
    private final TariffPriceRepository tariffPriceRepo;
    private final ProductCatalogBillingClient catalogClient;

    public RecordSubscriptionActivatedCommandHandler(
            SubscriberBillingRecordRepository subscriberRepo,
            TariffPriceRepository tariffPriceRepo,
            ProductCatalogBillingClient catalogClient) {
        this.subscriberRepo = subscriberRepo;
        this.tariffPriceRepo = tariffPriceRepo;
        this.catalogClient = catalogClient;
    }

    @Override
    @Transactional
    public Void handle(RecordSubscriptionActivatedCommand command) {
        if (subscriberRepo.findBySubscriptionId(command.subscriptionId()).isPresent()) {
            LOGGER.info("SubscriberBillingRecord already exists for subscriptionId={} — skipping",
                    command.subscriptionId());
            return null;
        }

        SubscriberBillingRecord record = SubscriberBillingRecord.activated(
                command.subscriptionId(), command.customerId(),
                command.tariffCode(), command.activatedAt());
        subscriberRepo.save(record);

        // Cache tariff price locally so bill-run needs no synchronous call.
        if (tariffPriceRepo.findByTariffCode(command.tariffCode()).isEmpty()) {
            try {
                TariffPricingResponse pricing = catalogClient.getTariffPricing(command.tariffCode());
                TariffPrice tp = TariffPrice.of(pricing.code() != null ? pricing.code() : command.tariffCode(),
                        pricing.monthlyFee(),
                        pricing.currency() != null ? pricing.currency() : "TRY",
                        Instant.now());
                tariffPriceRepo.save(tp);
                LOGGER.info("Cached tariff price tariffCode={} fee={}", command.tariffCode(), pricing.monthlyFee());
            } catch (ResourceNotFoundException e) {
                LOGGER.warn("Tariff {} not found in catalog — price not cached", command.tariffCode());
            }
        }

        LOGGER.info("Recorded subscription activation subscriptionId={} tariffCode={}",
                command.subscriptionId(), command.tariffCode());
        return null;
    }
}
