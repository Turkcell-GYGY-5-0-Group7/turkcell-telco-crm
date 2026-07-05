package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.GetTariffByIdQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Returns a single tariff by its primary key. Only {@link TariffStatus#ACTIVE} tariffs are served;
 * any other status is treated as not-found (404). Not cached: callers (e.g. order-service pricing
 * a new order) need the current price, and the existing code-keyed cache is only evicted by code
 * on price change (see {@link ChangeTariffPriceCommandHandler}), so caching by id here would risk
 * serving a stale price after a version bump.
 */
@Component
public class GetTariffByIdQueryHandler implements QueryHandler<GetTariffByIdQuery, TariffResponse> {

    private final TariffRepository tariffRepository;

    public GetTariffByIdQueryHandler(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public TariffResponse handle(GetTariffByIdQuery query) {
        Tariff tariff = tariffRepository.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Tariff not found with id: " + query.id(),
                        Map.of("id", query.id().toString())));

        if (tariff.getStatus() != TariffStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    CommonErrorCode.RESOURCE_NOT_FOUND,
                    "Tariff is not active: " + query.id(),
                    Map.of("id", query.id().toString(), "status", tariff.getStatus().name()));
        }

        return TariffResponse.from(tariff);
    }
}
