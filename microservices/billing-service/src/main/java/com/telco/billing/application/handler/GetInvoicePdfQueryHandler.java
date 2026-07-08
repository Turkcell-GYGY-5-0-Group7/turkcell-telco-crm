package com.telco.billing.application.handler;

import com.telco.billing.application.query.GetInvoicePdfQuery;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.billing.infrastructure.storage.StorageService;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetInvoicePdfQueryHandler implements QueryHandler<GetInvoicePdfQuery, byte[]> {

    private final InvoiceRepository invoiceRepo;
    private final StorageService storageService;

    public GetInvoicePdfQueryHandler(InvoiceRepository invoiceRepo, StorageService storageService) {
        this.invoiceRepo = invoiceRepo;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] handle(GetInvoicePdfQuery query) {
        Invoice invoice = invoiceRepo.findById(query.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found: " + query.invoiceId()));

        if (!query.callerIsAdmin()
                && (query.callerCustomerId() == null
                        || !query.callerCustomerId().equals(invoice.getCustomerId().toString()))) {
            throw new AccessDeniedException("Invoice does not belong to caller");
        }

        if (invoice.getPdfRef() == null) {
            throw new ResourceNotFoundException("PDF not yet available for invoice: " + query.invoiceId());
        }

        return storageService.fetch(invoice.getPdfRef());
    }
}
