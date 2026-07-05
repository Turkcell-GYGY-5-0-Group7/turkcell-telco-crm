package com.telco.billing.api;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoiceByIdQuery;
import com.telco.billing.application.query.GetInvoicePdfQuery;
import com.telco.billing.application.query.GetInvoicesQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    InvoiceController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    ApiResult<PageResult<InvoiceResponse>> listInvoices(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return responses.ok(mediator.query(new GetInvoicesQuery(
                customerId, page, size, authentication.getName(), isAdmin(authentication))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    ApiResult<InvoiceResponse> getInvoice(@PathVariable UUID id, Authentication authentication) {
        return responses.ok(mediator.query(new GetInvoiceByIdQuery(
                id, authentication.getName(), isAdmin(authentication))));
    }

    // Binary PDF download: ResponseEntity<byte[]> is the correct return type for file streams;
    // wrapping binary in ApiResult<T> is not meaningful for octet-stream responses.
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    ResponseEntity<byte[]> getInvoicePdf(@PathVariable UUID id, Authentication authentication) {
        byte[] pdf = mediator.query(new GetInvoicePdfQuery(
                id, authentication.getName(), isAdmin(authentication)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
