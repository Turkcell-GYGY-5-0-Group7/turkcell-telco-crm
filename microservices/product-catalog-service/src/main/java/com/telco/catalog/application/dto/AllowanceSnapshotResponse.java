package com.telco.catalog.application.dto;

/**
 * Lightweight usage-allowance snapshot returned by the internal endpoint
 * GET /api/v1/tariffs/{code}/allowance-snapshot.
 *
 * <p>Added to fix a real cross-service auth gap found via live acceptance testing, 2026-07-06:
 * usage-service's {@code ProvisionQuotaCommandHandler} (triggered from a Kafka consumer, which
 * holds no caller JWT to forward) was calling the authenticated {@code GET /api/v1/tariffs/{code}}
 * route and getting 401s on every quota provisioning attempt. Mirrors the already-established,
 * tech-lead-ruled pattern (14.1.1) used by order-service's price-snapshot and by-id lookups: a
 * tokenless, permitAll, minimal-projection endpoint for exactly this system-to-system read (no PII,
 * no ADMIN-only data - just the three usage allowances usage-service actually needs).
 */
public record AllowanceSnapshotResponse(
        String code,
        int minutesIncluded,
        int smsIncluded,
        int dataMbIncluded
) {
}
