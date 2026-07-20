package com.telco.usage.application.handler;

import com.telco.usage.application.command.ReprovisionQuotaCommand;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.client.ProductCatalogClient;
import com.telco.usage.infrastructure.client.TariffAllowanceResponse;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Re-provisions the current period's quota to the new tariff's allowances after a plan change
 * (Sprint 24 Feature 24.4, design-note D4): fetches the new allowances from
 * product-catalog-service, then {@link Quota#reprovision} preserves used amounts
 * ({@code remaining = max(0, newTotal - used)}) and recomputes the notification flags.
 *
 * <p>The quota row is loaded with the metering path's pessimistic write lock so a concurrent CDR
 * decrement cannot lost-update the counters. A missing quota row is TRANSIENT (activation
 * provisioning may still be in flight for a subscription that plan-changes immediately after
 * onboarding): the handler throws so the transaction - inbox row included - rolls back and Kafka
 * redelivers. Mirrors {@link TopUpQuotaCommandHandler}.
 */
@Component
public class ReprovisionQuotaCommandHandler implements CommandHandler<ReprovisionQuotaCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReprovisionQuotaCommandHandler.class);

    private final QuotaRepository quotaRepository;
    private final ProductCatalogClient productCatalogClient;

    public ReprovisionQuotaCommandHandler(QuotaRepository quotaRepository,
                                          ProductCatalogClient productCatalogClient) {
        this.quotaRepository = quotaRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Override
    public Void handle(ReprovisionQuotaCommand command) {
        TariffAllowanceResponse allowances =
                productCatalogClient.getTariffAllowances(command.newTariffCode());

        Quota quota = quotaRepository
                .findActiveForUpdateBySubscriptionId(command.subscriptionId(), command.changedAt())
                .orElseThrow(() -> new IllegalStateException(
                        "No active quota for subscription " + command.subscriptionId()
                                + " at " + command.changedAt() + " - activation provisioning may "
                                + "still be in flight; retry on redelivery"));

        quota.reprovision(
                allowances.minutesIncluded(),
                allowances.smsIncluded(),
                allowances.dataMbIncluded());
        quotaRepository.save(quota);

        LOGGER.info("Quota re-provisioned subscriptionId={} tariffCode={} totals minutes={} sms={} "
                        + "mb={} remaining minutes={} sms={} mb={}",
                command.subscriptionId(), command.newTariffCode(),
                quota.getMinutesTotal(), quota.getSmsTotal(), quota.getMbTotal(),
                quota.getMinutesRemaining(), quota.getSmsRemaining(), quota.getMbRemaining());
        return null;
    }
}
