package com.telco.billing.application.handler;

import com.telco.billing.application.query.GetInvoicesQuery;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetInvoicesQueryHandlerTest {

    @Mock private InvoiceRepository invoiceRepo;

    private GetInvoicesQueryHandler handler;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        handler = new GetInvoicesQueryHandler(invoiceRepo);
        customerId = UUID.randomUUID();
    }

    @Test
    void returns_paged_invoices_when_resolved_customer_id_matches() {
        Invoice invoice = Invoice.create(customerId, UUID.randomUUID(), Instant.now(), Instant.now(),
                BigDecimal.TEN, BigDecimal.ONE, "TRY", LocalDate.now().plusDays(30));
        Page<Invoice> page = new PageImpl<>(List.of(invoice), PageRequest.of(0, 20), 1);
        when(invoiceRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), any())).thenReturn(page);

        PageResult<?> result = handler.handle(
                new GetInvoicesQuery(customerId, 0, 20, "keycloak-sub", false, customerId.toString()));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void admin_can_list_invoices_for_any_customer_even_with_no_linked_customer_id() {
        Page<Invoice> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(invoiceRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerId), any())).thenReturn(page);

        PageResult<?> result = handler.handle(
                new GetInvoicesQuery(customerId, 0, 20, "admin-sub", true, null));

        assertThat(result.content()).isEmpty();
    }

    @Test
    void throws_access_denied_when_resolved_customer_id_does_not_match() {
        assertThatThrownBy(() -> handler.handle(
                new GetInvoicesQuery(customerId, 0, 20, "keycloak-sub", false, UUID.randomUUID().toString())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void throws_access_denied_when_caller_customer_id_is_null_unlinked_subscriber() {
        assertThatThrownBy(() -> handler.handle(
                new GetInvoicesQuery(customerId, 0, 20, "keycloak-sub", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
