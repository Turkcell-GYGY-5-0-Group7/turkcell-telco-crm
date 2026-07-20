package com.telco.billing.application.handler;

import com.telco.billing.application.command.ApplyDisputeAdjustmentCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyDisputeAdjustmentCommandHandlerTest {

    @Mock private InvoiceRepository invoiceRepo;

    private ApplyDisputeAdjustmentCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApplyDisputeAdjustmentCommandHandler(invoiceRepo);
    }

    private static Invoice newInvoice() {
        return Invoice.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(),
                new BigDecimal("50.00"), new BigDecimal("9.00"), "TRY", LocalDate.now().plusDays(14));
    }

    @Test
    void applies_adjustment_on_an_unpaid_held_invoice() {
        Invoice invoice = newInvoice();
        invoice.placeOnDisputeHold();
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ApplyDisputeAdjustmentCommand(invoice.getId(), new BigDecimal("10.00")));

        assertThat(invoice.getLines()).hasSize(1);
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo("49.00");
    }

    @Test
    void no_ops_when_invoice_already_paid_payment_service_owns_that_case() {
        Invoice invoice = newInvoice();
        invoice.issue();
        invoice.placeOnDisputeHold();
        invoice.markPaid();
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        handler.handle(new ApplyDisputeAdjustmentCommand(invoice.getId(), new BigDecimal("10.00")));

        assertThat(invoice.getLines()).isEmpty();
    }

    @Test
    void throws_not_found_when_invoice_does_not_exist() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ApplyDisputeAdjustmentCommand(invoiceId, BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
