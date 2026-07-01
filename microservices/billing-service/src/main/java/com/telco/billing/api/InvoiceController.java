package com.telco.billing.api;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoiceByIdQuery;
import com.telco.billing.application.query.GetInvoicesQuery;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.billing.infrastructure.storage.StorageService;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.mediator.Mediator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;
    private final InvoiceRepository invoiceRepo;
    private final StorageService storageService;

    InvoiceController(Mediator mediator, ApiResponseFactory responses,
                      InvoiceRepository invoiceRepo, StorageService storageService) {
        this.mediator = mediator;
        this.responses = responses;
        this.invoiceRepo = invoiceRepo;
        this.storageService = storageService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    ApiResult<PageResult<InvoiceResponse>> listInvoices(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        PageResult<InvoiceResponse> result = mediator.query(new GetInvoicesQuery(
                customerId, page, size, authentication.getName(), isAdmin(authentication)));
        return responses.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    ApiResult<InvoiceResponse> getInvoice(@PathVariable UUID id, Authentication authentication) {
        InvoiceResponse invoice = mediator.query(new GetInvoiceByIdQuery(
                id, authentication.getName(), isAdmin(authentication)));
        return responses.ok(invoice);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    ResponseEntity<byte[]> getInvoicePdf(@PathVariable UUID id, Authentication authentication) {
        InvoiceResponse invoice = mediator.query(new GetInvoiceByIdQuery(
                id, authentication.getName(), isAdmin(authentication)));
        if (invoice.pdfRef() == null) {
            throw new ResourceNotFoundException("PDF not yet available for invoice: " + id);
        }
        byte[] pdf = storageService.fetch(invoice.pdfRef());
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
