package com.telco.billing.application.handler;

import com.telco.billing.application.command.ClearInvoiceDisputeHoldCommand;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceDisputeStatus;
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
class ClearInvoiceDisputeHoldCommandHandlerTest {

    @Mock private InvoiceRepository invoiceRepo;

    private ClearInvoiceDisputeHoldCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClearInvoiceDisputeHoldCommandHandler(invoiceRepo);
    }

    @Test
    void clears_hold_on_the_invoice() {
        Invoice invoice = Invoice.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(),
                new BigDecimal("50.00"), new BigDecimal("9.00"), "TRY", LocalDate.now().plusDays(14));
        invoice.placeOnDisputeHold();
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new ClearInvoiceDisputeHoldCommand(invoice.getId()));

        assertThat(invoice.getDisputeStatus()).isEqualTo(InvoiceDisputeStatus.NONE);
    }

    @Test
    void throws_not_found_when_invoice_does_not_exist() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepo.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ClearInvoiceDisputeHoldCommand(invoiceId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
