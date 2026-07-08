package com.telco.billing.application.handler;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoiceByIdQuery;
import com.telco.billing.domain.Invoice;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetInvoiceByIdQueryHandler implements QueryHandler<GetInvoiceByIdQuery, InvoiceResponse> {

    private final InvoiceRepository invoiceRepo;

    public GetInvoiceByIdQueryHandler(InvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse handle(GetInvoiceByIdQuery query) {
        Invoice invoice = invoiceRepo.findById(query.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + query.invoiceId()));

        if (!query.callerIsAdmin()
                && (query.callerCustomerId() == null
                        || !query.callerCustomerId().equals(invoice.getCustomerId().toString()))) {
            throw new AccessDeniedException("Invoice does not belong to caller");
        }

        return InvoiceResponse.from(invoice);
    }
}
