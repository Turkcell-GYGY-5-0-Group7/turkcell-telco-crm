package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.GetTariffQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Returns a single tariff by code. Only {@link TariffStatus#ACTIVE} tariffs are served; any other
 * status is treated as not-found (404). Result is cached in Redis (feature 7.3.1, TTL 10 min).
 */
@Component
public class GetTariffQueryHandler implements QueryHandler<GetTariffQuery, TariffResponse> {

    private final TariffRepository tariffRepository;

    public GetTariffQueryHandler(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    @Cacheable(cacheNames = "tariffs", key = "#query.code()")
    public TariffResponse handle(GetTariffQuery query) {
        Tariff tariff = tariffRepository.findByCode(query.code())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Tariff not found with code: " + query.code(),
                        Map.of("code", query.code())));

        if (tariff.getStatus() != TariffStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    "Tariff is not active: " + query.code(),
                    Map.of("code", query.code(), "status", tariff.getStatus().name()));
        }

        return TariffResponse.from(tariff);
    }
}
