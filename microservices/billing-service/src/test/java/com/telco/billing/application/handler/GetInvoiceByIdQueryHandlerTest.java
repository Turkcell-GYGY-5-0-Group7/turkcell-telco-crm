package com.telco.billing.application.handler;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoiceByIdQuery;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
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
class GetInvoiceByIdQueryHandlerTest {

    @Mock private InvoiceRepository invoiceRepo;

    private GetInvoiceByIdQueryHandler handler;
    private UUID customerId;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        handler = new GetInvoiceByIdQueryHandler(invoiceRepo);
        customerId = UUID.randomUUID();
        invoice = Invoice.create(customerId, UUID.randomUUID(), Instant.now(), Instant.now(),
                BigDecimal.TEN, BigDecimal.ONE, "TRY", LocalDate.now().plusDays(30));
    }

    @Test
    void returns_invoice_when_resolved_customer_id_matches_the_owner() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        InvoiceResponse response = handler.handle(
                new GetInvoiceByIdQuery(invoice.getId(), "keycloak-sub", false, customerId.toString()));

        assertThat(response.id()).isEqualTo(invoice.getId());
    }

    @Test
    void returns_invoice_when_caller_is_admin_regardless_of_owner() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        InvoiceResponse response = handler.handle(
                new GetInvoiceByIdQuery(invoice.getId(), "admin-sub", true, null));

        assertThat(response.id()).isEqualTo(invoice.getId());
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match_the_owner() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> handler.handle(
                new GetInvoiceByIdQuery(invoice.getId(), "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        when(invoiceRepo.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> handler.handle(
                new GetInvoiceByIdQuery(invoice.getId(), "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_not_found_when_invoice_does_not_exist() {
        UUID missingId = UUID.randomUUID();
        when(invoiceRepo.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new GetInvoiceByIdQuery(missingId, "keycloak-sub", false, customerId.toString())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
