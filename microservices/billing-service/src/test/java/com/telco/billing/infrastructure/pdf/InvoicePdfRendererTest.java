package com.telco.billing.infrastructure.pdf;

import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    void renders_a_valid_pdf_document_for_an_invoice_with_lines() {
        Instant periodStart = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant periodEnd = periodStart.plusSeconds(2_592_000);
        Invoice invoice = Invoice.create(
                UUID.randomUUID(), UUID.randomUUID(), periodStart, periodEnd,
                new BigDecimal("100.00"), new BigDecimal("18.00"), "TRY",
                periodEnd.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(30));
        InvoiceLine.of(invoice, "Monthly tariff: POSTPAID-M", BigDecimal.ONE, new BigDecimal("100.00"));
        invoice.issue();

        byte[] pdf = renderer.render(invoice);

        assertThat(pdf).isNotEmpty();
        // PDF magic header: every valid PDF document starts with "%PDF-".
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void renders_a_valid_pdf_document_for_an_invoice_with_no_lines() {
        Instant periodStart = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant periodEnd = periodStart.plusSeconds(2_592_000);
        Invoice invoice = Invoice.create(
                UUID.randomUUID(), UUID.randomUUID(), periodStart, periodEnd,
                BigDecimal.ZERO, BigDecimal.ZERO, "TRY",
                periodEnd.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(30));

        byte[] pdf = renderer.render(invoice);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
