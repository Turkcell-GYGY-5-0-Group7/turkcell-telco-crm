package com.telco.webbff.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Partial view of billing-service's invoice read model, deserialized from the gateway response
 * ({@code /api/v1/invoices?customerId=...}). web-bff needs the id (to build the PDF-download link and
 * shape the UI row), the period bounds (to derive the {@code yyyy-MM} label), the payable total,
 * currency and status. Unknown properties (lines, tax breakdown, {@code pdfRef}, timestamps) are
 * ignored. Local DTO; no cross-service coupling (ADR-022).
 */
public record GatewayInvoice(
        UUID id,
        UUID customerId,
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal grandTotal,
        String currency,
        String status) {
}
