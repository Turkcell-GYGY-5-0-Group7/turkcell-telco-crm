package com.telco.usage.application.handler;

import com.telco.usage.application.command.ProvisionQuotaCommand;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.client.ProductCatalogClient;
import com.telco.usage.infrastructure.client.TariffAllowanceResponse;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Provisions the initial quota for a newly activated subscription (FR-17, FR-18).
 *
 * <p>Fetches the tariff's included allowances from product-catalog-service, computes the current
 * calendar-month billing period (UTC), and persists a {@link Quota} row. The
 * {@code uidx_quotas_subscription_period} unique index makes this idempotent: a duplicate
 * activation event from the inbox simply hits a constraint violation which is caught and ignored.
 */
@Component
public class ProvisionQuotaCommandHandler implements CommandHandler<ProvisionQuotaCommand, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionQuotaCommandHandler.class);

    private final QuotaRepository quotaRepository;
    private final ProductCatalogClient productCatalogClient;

    public ProvisionQuotaCommandHandler(QuotaRepository quotaRepository,
                                        ProductCatalogClient productCatalogClient) {
        this.quotaRepository = quotaRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Override
    @Transactional
    public Void handle(ProvisionQuotaCommand command) {
        TariffAllowanceResponse allowances =
                productCatalogClient.getTariffAllowances(command.tariffCode());

        Instant periodStart = monthStart(command.activatedAt());
        Instant periodEnd = monthStart(command.activatedAt()).atZone(ZoneOffset.UTC)
                .plusMonths(1).toInstant();

        Quota quota = Quota.create(
                command.subscriptionId(),
                command.customerId(),
                periodStart,
                periodEnd,
                allowances.minutesIncluded(),
                allowances.smsIncluded(),
                allowances.dataMbIncluded());

        if (quotaRepository.existsBySubscriptionIdAndPeriodStart(command.subscriptionId(), periodStart)) {
            LOGGER.info("Quota already exists for subscriptionId={} periodStart={} — skipping",
                    command.subscriptionId(), periodStart);
            return null;
        }

        quotaRepository.save(quota);
        LOGGER.info("Quota provisioned subscriptionId={} tariffCode={} period=[{}, {}) "
                        + "minutes={} sms={} mb={}",
                command.subscriptionId(), command.tariffCode(),
                periodStart, periodEnd,
                allowances.minutesIncluded(), allowances.smsIncluded(),
                allowances.dataMbIncluded());

        return null;
    }

    private static Instant monthStart(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }
}
