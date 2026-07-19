package com.telco.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provisional-hold-vs-real-financial-action invariant (ADR-028 Section 5, Sprint 22 Feature 22.4.2/
 * 22.4.3's own load-bearing acceptance criteria): {@code placeOnDisputeHold}/{@code clearDisputeHold}
 * must change {@code disputeStatus} and NOTHING else - {@code grandTotal}/{@code subTotal}/{@code tax}/
 * {@code status} bit-for-bit identical before and after. Only {@code applyDisputeAdjustment} on an
 * ON_HOLD invoice adds a line and changes the total.
 */
class InvoiceDisputeTest {

    private static Invoice newInvoice() {
        return Invoice.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(),
                new BigDecimal("100.00"), new BigDecimal("18.00"), "TRY", LocalDate.now().plusDays(14));
    }

    @Test
    void new_invoice_has_no_dispute_hold() {
        assertThat(newInvoice().getDisputeStatus()).isEqualTo(InvoiceDisputeStatus.NONE);
    }

    @Test
    void placeOnDisputeHold_changes_only_disputeStatus() {
        Invoice invoice = newInvoice();
        BigDecimal subTotalBefore = invoice.getSubTotal();
        BigDecimal grandTotalBefore = invoice.getGrandTotal();
        BigDecimal taxBefore = invoice.getTax();
        InvoiceStatus statusBefore = invoice.getStatus();
        int lineCountBefore = invoice.getLines().size();

        invoice.placeOnDisputeHold();

        assertThat(invoice.getDisputeStatus()).isEqualTo(InvoiceDisputeStatus.ON_HOLD);
        assertThat(invoice.getSubTotal()).isEqualByComparingTo(subTotalBefore);
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(grandTotalBefore);
        assertThat(invoice.getTax()).isEqualByComparingTo(taxBefore);
        assertThat(invoice.getStatus()).isEqualTo(statusBefore);
        assertThat(invoice.getLines()).hasSize(lineCountBefore);
    }

    @Test
    void clearDisputeHold_changes_only_disputeStatus() {
        Invoice invoice = newInvoice();
        invoice.placeOnDisputeHold();
        BigDecimal subTotalBefore = invoice.getSubTotal();
        BigDecimal grandTotalBefore = invoice.getGrandTotal();
        int lineCountBefore = invoice.getLines().size();

        invoice.clearDisputeHold();

        assertThat(invoice.getDisputeStatus()).isEqualTo(InvoiceDisputeStatus.NONE);
        assertThat(invoice.getSubTotal()).isEqualByComparingTo(subTotalBefore);
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(grandTotalBefore);
        assertThat(invoice.getLines()).hasSize(lineCountBefore);
    }

    @Test
    void applyDisputeAdjustment_on_held_invoice_adds_adjustment_line_and_reduces_totals() {
        Invoice invoice = newInvoice();
        invoice.placeOnDisputeHold();
        BigDecimal subTotalBefore = invoice.getSubTotal();
        BigDecimal grandTotalBefore = invoice.getGrandTotal();

        invoice.applyDisputeAdjustment(new BigDecimal("20.00"));

        assertThat(invoice.getDisputeStatus()).isEqualTo(InvoiceDisputeStatus.NONE);
        assertThat(invoice.getSubTotal()).isEqualByComparingTo(subTotalBefore.subtract(new BigDecimal("20.00")));
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(grandTotalBefore.subtract(new BigDecimal("20.00")));
        assertThat(invoice.getLines()).hasSize(1);
        assertThat(invoice.getLines().get(0).getLineType()).isEqualTo(InvoiceLineType.ADJUSTMENT);
        assertThat(invoice.getLines().get(0).getLineTotal()).isEqualByComparingTo(new BigDecimal("-20.00"));
    }

    @Test
    void applyDisputeAdjustment_is_a_no_op_when_not_on_hold() {
        Invoice invoice = newInvoice();
        BigDecimal subTotalBefore = invoice.getSubTotal();
        BigDecimal grandTotalBefore = invoice.getGrandTotal();

        // never placed on hold - the check-then-act guard must no-op, not throw, not append a line.
        invoice.applyDisputeAdjustment(new BigDecimal("20.00"));

        assertThat(invoice.getLines()).isEmpty();
        assertThat(invoice.getSubTotal()).isEqualByComparingTo(subTotalBefore);
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(grandTotalBefore);
    }

    @Test
    void applyDisputeAdjustment_is_a_no_op_when_hold_already_cleared() {
        Invoice invoice = newInvoice();
        invoice.placeOnDisputeHold();
        invoice.clearDisputeHold();
        BigDecimal subTotalBefore = invoice.getSubTotal();

        // Simulates redelivery after resolution already applied elsewhere - must not double-adjust.
        invoice.applyDisputeAdjustment(new BigDecimal("20.00"));

        assertThat(invoice.getLines()).isEmpty();
        assertThat(invoice.getSubTotal()).isEqualByComparingTo(subTotalBefore);
    }
}
