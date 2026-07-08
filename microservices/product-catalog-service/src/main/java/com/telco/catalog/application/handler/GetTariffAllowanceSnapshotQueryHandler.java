package com.telco.catalog.application.handler;

import com.telco.catalog.application.dto.AllowanceSnapshotResponse;
import com.telco.catalog.application.query.GetTariffAllowanceSnapshotQuery;
import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffStatus;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Returns a lightweight usage-allowance snapshot for a tariff code. Used by usage-service's
 * {@code ProvisionQuotaCommandHandler} (a Kafka-consumer-triggered write with no caller JWT to
 * forward) to provision a subscription's quota after activation. No auth required (tech-lead
 * ruling 14.1.1, mirrors {@link GetTariffPriceSnapshotQueryHandler}). Only serves
 * {@link TariffStatus#ACTIVE} tariffs; all others return 404.
 */
@Component
public class GetTariffAllowanceSnapshotQueryHandler
        implements QueryHandler<GetTariffAllowanceSnapshotQuery, AllowanceSnapshotResponse> {

    private final TariffRepository tariffRepository;

    public GetTariffAllowanceSnapshotQueryHandler(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    @Override
    public AllowanceSnapshotResponse handle(GetTariffAllowanceSnapshotQuery query) {
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

        return new AllowanceSnapshotResponse(
                tariff.getCode(),
                tariff.getMinutesIncluded(),
                tariff.getSmsIncluded(),
                tariff.getDataMbIncluded()
        );
    }
}
