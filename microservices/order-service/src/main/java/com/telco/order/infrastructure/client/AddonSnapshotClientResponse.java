package com.telco.order.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight DTO for the product-catalog-service {@code GET /internal/addons/{code}/snapshot}
 * response body (Sprint 24 Features 24.1/24.2). Mirrors catalog's {@code AddonSnapshotResponse};
 * unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>{@code price} prices the ADDON order item; {@code name} becomes the item's product-name
 * snapshot; {@code dataMb}/{@code voiceMinutes}/{@code smsCount} are the allowance deltas
 * snapshotted onto the item at creation time (design-note D1: the event stays immutable, no
 * runtime catalog coupling for usage-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddonSnapshotClientResponse(
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
}
