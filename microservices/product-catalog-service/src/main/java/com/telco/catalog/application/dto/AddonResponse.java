package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.Addon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read DTO for an addon bundle. Domain entities are never exposed directly (ADR-015). */
public record AddonResponse(
        UUID id,
        String code,
        String name,
        BigDecimal price,
        String currency,
        String type,
        int validityDays,
        String status,
        Instant createdAt
) {

    public static AddonResponse from(Addon addon) {
        return new AddonResponse(
                addon.getId(),
                addon.getCode(),
                addon.getName(),
                addon.getPrice(),
                addon.getCurrency(),
                addon.getType().name(),
                addon.getValidityDays(),
                addon.getStatus(),
                addon.getCreatedAt()
        );
    }
}
