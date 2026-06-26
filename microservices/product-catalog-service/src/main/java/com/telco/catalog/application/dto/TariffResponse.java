package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.Tariff;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read DTO for a tariff. Domain entities are never exposed directly (ADR-015). */
public record TariffResponse(
        UUID id,
        String code,
        String name,
        String type,
        String status,
        BigDecimal monthlyFee,
        String currency,
        int minutesIncluded,
        int smsIncluded,
        int dataMbIncluded,
        String targetSegment,
        Instant effectiveFrom,
        Instant effectiveTo,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public static TariffResponse from(Tariff tariff) {
        return new TariffResponse(
                tariff.getId(),
                tariff.getCode(),
                tariff.getName(),
                tariff.getType().name(),
                tariff.getStatus().name(),
                tariff.getMonthlyFee(),
                tariff.getCurrency(),
                tariff.getMinutesIncluded(),
                tariff.getSmsIncluded(),
                tariff.getDataMbIncluded(),
                tariff.getTargetSegment(),
                tariff.getEffectiveFrom(),
                tariff.getEffectiveTo(),
                tariff.getVersion(),
                tariff.getCreatedAt(),
                tariff.getUpdatedAt()
        );
    }
}
