package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.application.query.GetAddonByCodeQuery;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/** Reads one addon by code; cached under the addons cache create-addon already evicts. */
@Component
public class GetAddonByCodeQueryHandler implements QueryHandler<GetAddonByCodeQuery, AddonResponse> {

    private final AddonRepository addonRepository;

    public GetAddonByCodeQueryHandler(AddonRepository addonRepository) {
        this.addonRepository = addonRepository;
    }

    @Override
    @Cacheable(cacheNames = "addons", key = "'code:' + #query.code()")
    public AddonResponse handle(GetAddonByCodeQuery query) {
        return addonRepository.findByCode(query.code())
                .map(AddonResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("addon not found: " + query.code()));
    }
}
