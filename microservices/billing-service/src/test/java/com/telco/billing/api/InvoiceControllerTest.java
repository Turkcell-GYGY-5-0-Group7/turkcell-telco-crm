package com.telco.billing.api;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoiceByIdQuery;
import com.telco.billing.application.query.GetInvoicePdfQuery;
import com.telco.billing.application.query.GetInvoicesQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/** No business logic lives here (ADR-008) — confirms request wiring, admin/owner authority
 *  derivation, and the binary-PDF response shape. */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    @Mock private Mediator mediator;
    @Mock private ApiResponseFactory responses;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private Authentication subscriberAuth;
    @Mock private Authentication adminAuth;

    private InvoiceController controller;

    @BeforeEach
    void setUp() {
        controller = new InvoiceController(mediator, responses, currentUserProvider);
    }

    @Test
    void listInvoices_passes_caller_identity_and_admin_flag_through_to_the_query() {
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn("keycloak-sub");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("keycloak-sub", Set.of("SUBSCRIBER"), null, customerId.toString()));
        PageResult<InvoiceResponse> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        when(mediator.query(new GetInvoicesQuery(
                        customerId, 0, 20, null, "keycloak-sub", false, customerId.toString())))
                .thenReturn(page);
        when(responses.ok(page)).thenReturn(ApiResult.ok(page, null));

        ApiResult<PageResult<InvoiceResponse>> response =
                controller.listInvoices(customerId, 0, 20, null, subscriberAuth);

        assertThat(response.data()).isEqualTo(page);
    }

    @Test
    void getInvoice_marks_caller_as_admin_when_role_admin_authority_present() {
        UUID invoiceId = UUID.randomUUID();
        when(adminAuth.getName()).thenReturn("admin-user");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(adminAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("admin-user", Set.of("ADMIN"), null, null));
        InvoiceResponse invoiceResponse = new InvoiceResponse(
                invoiceId, UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, null, null, "TRY", null, null, null, null, null, List.of());
        when(mediator.query(new GetInvoiceByIdQuery(invoiceId, "admin-user", true, null)))
                .thenReturn(invoiceResponse);
        when(responses.ok(invoiceResponse)).thenReturn(ApiResult.ok(invoiceResponse, null));

        ApiResult<InvoiceResponse> response = controller.getInvoice(invoiceId, adminAuth);

        assertThat(response.data()).isEqualTo(invoiceResponse);
    }

    @Test
    void getInvoicePdf_returns_pdf_content_type_and_attachment_header() {
        UUID invoiceId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn("user-1");
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext("user-1", Set.of("SUBSCRIBER"), null, customerId.toString()));
        byte[] pdfBytes = "%PDF-1.4".getBytes();
        when(mediator.query(new GetInvoicePdfQuery(invoiceId, "user-1", false, customerId.toString())))
                .thenReturn(pdfBytes);

        ResponseEntity<byte[]> response = controller.getInvoicePdf(invoiceId, subscriberAuth);

        assertThat(response.getBody()).isEqualTo(pdfBytes);
        assertThat(response.getHeaders().getContentDisposition().toString())
                .contains("invoice-" + invoiceId + ".pdf");
    }
}
