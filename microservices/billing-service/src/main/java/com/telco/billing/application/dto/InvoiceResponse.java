package com.telco.billing.application.dto;

import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID customerId,
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal subTotal,
        BigDecimal tax,
        BigDecimal grandTotal,
        String currency,
        InvoiceStatus status,
        LocalDate dueDate,
        Instant issuedAt,
        String pdfRef,
        Instant createdAt,
        List<InvoiceLineResponse> lines
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCustomerId(),
                invoice.getSubscriptionId(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getSubTotal(),
                invoice.getTax(),
                invoice.getGrandTotal(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getIssuedAt(),
                invoice.getPdfRef(),
                invoice.getCreatedAt(),
                invoice.getLines().stream().map(InvoiceLineResponse::from).toList()
        );
    }
}
