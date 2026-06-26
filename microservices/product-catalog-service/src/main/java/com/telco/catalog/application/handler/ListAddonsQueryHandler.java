package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.application.query.ListAddonsQuery;
import com.telco.catalog.infrastructure.persistence.AddonRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Returns a paginated list of addons, optionally filtered by tariff code (feature 7.4.3).
 * Result is cached per tariff code in Redis (feature 7.3.1, TTL 5 min).
 */
@Component
public class ListAddonsQueryHandler implements QueryHandler<ListAddonsQuery, PageResult<AddonResponse>> {

    private final AddonRepository addonRepository;

    public ListAddonsQueryHandler(AddonRepository addonRepository) {
        this.addonRepository = addonRepository;
    }

    @Override
    @Cacheable(cacheNames = "addons", key = "#query.tariffCode() != null ? #query.tariffCode() : 'all'")
    public PageResult<AddonResponse> handle(ListAddonsQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());

        Page<AddonResponse> page;
        if (query.tariffCode() != null && !query.tariffCode().isBlank()) {
            page = addonRepository.findByTariffs_Code(query.tariffCode(), pageable)
                    .map(AddonResponse::from);
        } else {
            page = addonRepository.findAll(pageable).map(AddonResponse::from);
        }

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
