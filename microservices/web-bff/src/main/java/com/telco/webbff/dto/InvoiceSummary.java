package com.telco.webbff.dto;

import java.math.BigDecimal;

/**
 * UI-shaped view of one invoice, composed from billing-service. {@code pdfUrl} is the gateway route
 * {@code /api/v1/invoices/{id}/pdf} that billing-service streams the rendered PDF from: the browser
 * downloads it directly through the gateway with its own bearer token. The BFF never proxies the PDF
 * bytes (ADR-022). billing-service does not expose a pre-signed object-store URL on the list response,
 * so the authenticated gateway route is the usable link.
 */
public record InvoiceSummary(
        String invoiceId,
        String period,
        BigDecimal amount,
        String currency,
        String status,
        String pdfUrl) {
}
