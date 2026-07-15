package com.telco.webbff.dto;

import java.util.List;

/**
 * Paged invoice-list composition for {@code GET /bff/v1/invoices}: the caller's invoices for the
 * requested page, each carrying a usable PDF-download link, plus the paging metadata mirrored from
 * billing-service so the UI can render pagination (web-bff contract). UI DTO, not {@code ApiResult<T>}
 * (ADR-015 BFF exception). Scoped strictly to the authenticated caller.
 */
public record InvoicesResponse(
        List<InvoiceSummary> invoices,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
