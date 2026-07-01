package com.telco.billing.application.dto;

import com.telco.billing.domain.InvoiceLine;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
        UUID id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(), line.getDescription(),
                line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
    }
}
