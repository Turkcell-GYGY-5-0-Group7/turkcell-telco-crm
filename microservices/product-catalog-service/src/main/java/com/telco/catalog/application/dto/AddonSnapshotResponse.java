package com.telco.catalog.application.dto;

import com.telco.catalog.domain.model.Addon;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal, system-to-system snapshot of an addon (GET /internal/addons/{code}/snapshot).
 * Consumed by order-service for addon order pricing and allowance snapshotting (feature 24.1,
 * Sprint 24 design note D1). No PII.
 */
public record AddonSnapshotResponse(
        UUID id,
        String code,
        String name,
        String type,
        BigDecimal price,
        String currency,
        int validityDays,
        Long dataMb,
        Long voiceMinutes,
        Long smsCount
) {

    public static AddonSnapshotResponse from(Addon addon) {
        return new AddonSnapshotResponse(
                addon.getId(),
                addon.getCode(),
                addon.getName(),
                addon.getType().name(),
                addon.getPrice(),
                addon.getCurrency(),
                addon.getValidityDays(),
                addon.getDataMb(),
                addon.getVoiceMinutes(),
                addon.getSmsCount()
        );
    }
}
