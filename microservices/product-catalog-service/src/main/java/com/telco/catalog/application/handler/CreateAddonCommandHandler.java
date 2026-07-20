package com.telco.catalog.application.handler;

import com.telco.catalog.application.command.CreateAddonCommand;
import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.domain.model.Addon;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.cqrs.CommandHandler;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Creates a new addon (FR-05) and evicts the addon list cache so reads see it immediately.
 * No domain event is published: the governed catalog contract defines {@code tariff.created.v1}
 * and {@code tariff.price-changed.v1} only; an addon lifecycle event would be a new Avro contract
 * (event-integration scope), and no current consumer needs one.
 */
@Component
public class CreateAddonCommandHandler implements CommandHandler<CreateAddonCommand, AddonResponse> {

    private final AddonRepository addonRepository;

    public CreateAddonCommandHandler(AddonRepository addonRepository) {
        this.addonRepository = addonRepository;
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
        return AddonResponse.from(addon);
    }
}
