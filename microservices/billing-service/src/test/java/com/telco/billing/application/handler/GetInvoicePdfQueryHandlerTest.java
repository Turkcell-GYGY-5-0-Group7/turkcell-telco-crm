package com.telco.billing.application.handler;

import com.telco.billing.application.query.GetInvoicePdfQuery;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.billing.infrastructure.storage.StorageService;
import com.telco.platform.common.exception.AccessDeniedException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInvoicePdfQueryHandlerTest {

    @Mock private InvoiceRepository invoiceRepo;
    @Mock private StorageService storageService;

    private GetInvoicePdfQueryHandler handler;
    private UUID customerId;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        handler = new GetInvoicePdfQueryHandler(invoiceRepo, storageService);
        customerId = UUID.randomUUID();
        invoice = Invoice.create(customerId, UUID.randomUUID(), Instant.now(), Instant.now(),
                BigDecimal.TEN, BigDecimal.ONE, "TRY", LocalDate.now().plusDays(30));
    }

    @Test
    void returns_pdf_bytes_when_resolved_customer_id_matches_the_owner() {
        invoice.attachPdf("file:///tmp/billing-pdfs/invoices/" + invoice.getId() + ".pdf");
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(storageService.fetch(invoice.getPdfRef())).thenReturn("pdf-bytes".getBytes());

        byte[] pdf = handler.handle(
                new GetInvoicePdfQuery(invoice.getId(), "keycloak-sub", false, customerId.toString()));

        assertThat(pdf).isEqualTo("pdf-bytes".getBytes());
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match_the_owner() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> handler.handle(
                new GetInvoicePdfQuery(invoice.getId(), "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> handler.handle(
                new GetInvoicePdfQuery(invoice.getId(), "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_not_found_when_pdf_not_yet_generated() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> handler.handle(
                new GetInvoicePdfQuery(invoice.getId(), "keycloak-sub", false, customerId.toString())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PDF not yet available");
    }

    @Test
    void throws_not_found_when_invoice_does_not_exist() {
        UUID missingId = UUID.randomUUID();
        when(invoiceRepo.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new GetInvoicePdfQuery(missingId, "admin-sub", true, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
