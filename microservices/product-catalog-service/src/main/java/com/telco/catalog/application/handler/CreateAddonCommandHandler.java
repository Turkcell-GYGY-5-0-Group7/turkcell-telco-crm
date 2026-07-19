package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateAddonCommand;
import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates a new addon and links it to the requested tariffs (FR-05, feature 24.1). The
 * {@code tariff_addons} join table is owned by {@code Tariff.addons}, so links are attached via
 * {@link Tariff#addAddon(Addon)} and persisted through the tariff repository - join rows never
 * persist from the addon side. The mediator TransactionBehavior wraps this handler so the addon
 * insert and all join rows commit atomically (ADR-005).
 *
 * <p>Cache: evicts the whole {@code addons} cache because a new addon invalidates both the
 * unfiltered list entry ({@code 'all'}) and every linked tariff-code entry. The {@code tariffs}
 * cache is untouched - {@code TariffResponse} carries no addon data.
 */
@Component
public class CreateAddonCommandHandler implements CommandHandler<CreateAddonCommand, AddonResponse> {

    private final AddonRepository addonRepository;
    private final TariffRepository tariffRepository;

    public CreateAddonCommandHandler(AddonRepository addonRepository,
                                     TariffRepository tariffRepository) {
        this.addonRepository = addonRepository;
        this.tariffRepository = tariffRepository;
    }

    @Override
    @CacheEvict(cacheNames = "addons", allEntries = true)
    public AddonResponse handle(CreateAddonCommand command) {
        if (addonRepository.existsByCode(command.code())) {
            throw new BusinessRuleException(
                    "Addon with code '" + command.code() + "' already exists");
        }

        Addon addon = Addon.create(
                command.code(),
                command.name(),
                command.price(),
                command.currency(),
                command.type(),
                command.validityDays(),
                command.dataMb(),
                command.voiceMinutes(),
                command.smsCount()
        );

        addonRepository.save(addon);

        Set<String> tariffCodes = command.applicableTariffCodes();
        if (tariffCodes != null) {
            for (String tariffCode : tariffCodes) {
                Tariff tariff = tariffRepository.findByCode(tariffCode)
                        .orElseThrow(() -> new BusinessRuleException(
                                "Unknown tariff code: " + tariffCode));
                tariff.addAddon(addon);
                tariffRepository.save(tariff);
            }
        }

        return AddonResponse.from(addon);
    }
}
