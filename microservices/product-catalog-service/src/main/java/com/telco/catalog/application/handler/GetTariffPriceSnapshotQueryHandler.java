package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.PriceSnapshotResponse;
import com.telco.catalog.application.query.GetTariffPriceSnapshotQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Returns a lightweight price snapshot for a tariff code (feature 7.4.4).
 * Used by order-service for price resolution at order creation time. No auth required.
 * Only serves {@link TariffStatus#ACTIVE} tariffs; all others return 404.
 */
@Component
public class GetTariffPriceSnapshotQueryHandler
        implements QueryHandler<GetTariffPriceSnapshotQuery, PriceSnapshotResponse> {

    private final TariffRepository tariffRepository;

    public GetTariffPriceSnapshotQueryHandler(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public PriceSnapshotResponse handle(GetTariffPriceSnapshotQuery query) {
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

        return new PriceSnapshotResponse(
                tariff.getCode(),
                tariff.getMonthlyFee(),
                tariff.getCurrency(),
                tariff.getVersion(),
                tariff.getEffectiveFrom()
        );
    }
}
