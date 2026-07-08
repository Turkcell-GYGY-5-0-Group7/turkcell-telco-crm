package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateTariffCommand;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.domain.event.TariffCreatedEvent;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.service.TariffVersioningService;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Creates a new tariff, persists the initial version snapshot, publishes {@code tariff.created.v1}
 * through the transactional outbox, and evicts any stale cache entry. The mediator
 * TransactionBehavior wraps this handler in a transaction so the JPA insert, version snapshot, and
 * outbox row commit atomically (ADR-005, ADR-009).
 */
@Component
public class CreateTariffCommandHandler implements CommandHandler<CreateTariffCommand, TariffResponse> {

    private static final String AGGREGATE_TYPE = "tariff";
    private static final String EVENT_TYPE = "tariff.created.v1";

    private final TariffRepository tariffRepository;
    private final TariffVersioningService versioningService;
    private final OutboxService outboxService;

    public CreateTariffCommandHandler(TariffRepository tariffRepository,
                                      TariffVersioningService versioningService,
                                      OutboxService outboxService) {
        this.tariffRepository = tariffRepository;
        this.versioningService = versioningService;
        this.outboxService = outboxService;
    }

    @Override
    @CacheEvict(cacheNames = "tariffs", key = "#command.code()")
    public TariffResponse handle(CreateTariffCommand command) {
        if (tariffRepository.existsByCode(command.code())) {
            throw new BusinessRuleException(
                    "Tariff with code '" + command.code() + "' already exists");
        }

        Tariff tariff = Tariff.create(
                command.code(),
                command.name(),
                command.type(),
                command.monthlyFee(),
                command.currency(),
                command.minutesIncluded(),
                command.smsIncluded(),
                command.dataMbIncluded(),
                command.targetSegment(),
                command.effectiveFrom(),
                command.effectiveTo()
        );

        tariff.activate();

        tariffRepository.save(tariff);
        versioningService.createInitialSnapshot(tariff);

        outboxService.publish(
                AGGREGATE_TYPE,
                tariff.getId().toString(),
                EVENT_TYPE,
                new TariffCreatedEvent(
                        tariff.getId().toString(),
                        tariff.getCode(),
                        tariff.getName(),
                        tariff.getType().name(),
                        tariff.getMonthlyFee(),
                        tariff.getCurrency(),
                        tariff.getEffectiveFrom().toString(),
                        tariff.getCreatedAt().toString()
                )
        );

        return TariffResponse.from(tariff);
    }
}
