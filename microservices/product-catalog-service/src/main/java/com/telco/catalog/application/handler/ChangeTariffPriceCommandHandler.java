package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.ChangeTariffPriceCommand;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.domain.event.TariffPriceChangedEvent;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.service.TariffVersioningService;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Applies a price change to an existing tariff. The {@link TariffVersioningService} bumps the
 * version and persists an immutable snapshot before the new price is committed. Publishes
 * {@code tariff.price-changed.v1} via the outbox. Cache entry for the tariff code is evicted.
 */
@Component
public class ChangeTariffPriceCommandHandler
        implements CommandHandler<ChangeTariffPriceCommand, TariffResponse> {

    private static final String AGGREGATE_TYPE = "Tariff";
    private static final String EVENT_TYPE = "tariff.price-changed.v1";

    private final TariffRepository tariffRepository;
    private final TariffVersioningService versioningService;
    private final OutboxService outboxService;

    public ChangeTariffPriceCommandHandler(TariffRepository tariffRepository,
                                           TariffVersioningService versioningService,
                                           OutboxService outboxService) {
        this.tariffRepository = tariffRepository;
        this.versioningService = versioningService;
        this.outboxService = outboxService;
    }

    @Override
    @CacheEvict(cacheNames = "tariffs", key = "#command.tariffCode()")
    public TariffResponse handle(ChangeTariffPriceCommand command) {
        Tariff tariff = tariffRepository.findByCode(command.tariffCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Tariff not found with code: " + command.tariffCode(),
                        Map.of("code", command.tariffCode())));

        BigDecimal oldFee = tariff.getMonthlyFee();

        versioningService.applyPriceChange(tariff, command.newMonthlyFee());
        tariffRepository.save(tariff);

        outboxService.publish(
                AGGREGATE_TYPE,
                tariff.getId().toString(),
                EVENT_TYPE,
                new TariffPriceChangedEvent(
                        tariff.getId().toString(),
                        tariff.getCode(),
                        oldFee,
                        tariff.getMonthlyFee(),
                        tariff.getCurrency(),
                        tariff.getVersion(),
                        Instant.now().toString()
                )
        );

        return TariffResponse.from(tariff);
    }
}
