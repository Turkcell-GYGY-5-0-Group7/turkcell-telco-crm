package com.telco.billing.application.handler;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.billing.application.query.GetInvoicesQuery;
import com.telco.billing.infrastructure.persistence.InvoiceRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetInvoicesQueryHandler implements QueryHandler<GetInvoicesQuery, PageResult<InvoiceResponse>> {

    private final InvoiceRepository invoiceRepo;

    public GetInvoicesQueryHandler(InvoiceRepository invoiceRepo) {
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InvoiceResponse> handle(GetInvoicesQuery query) {
        if (!query.callerIsAdmin()
                && !query.customerId().toString().equals(query.callerUserId())) {
            throw new AccessDeniedException("Cannot list invoices for another customer");
        }

        Page<InvoiceResponse> page = invoiceRepo.findByCustomerIdOrderByCreatedAtDesc(
                query.customerId(), PageRequest.of(query.page(), query.size()))
                .map(InvoiceResponse::from);

        return new PageResult<>(page.getContent(), query.page(), query.size(),
                page.getTotalElements(), page.getTotalPages());
    }
}
