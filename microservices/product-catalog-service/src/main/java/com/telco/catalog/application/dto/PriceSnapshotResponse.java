package com.telco.catalog.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight price snapshot returned by the internal endpoint
 * GET /api/v1/tariffs/{code}/price-snapshot (feature 7.4.4).
 * Consumed by order-service; no auth required.
 */
public record PriceSnapshotResponse(
        String code,
        BigDecimal monthlyFee,
        String currency,
        int version,
        Instant effectiveFrom
) {
}
