package com.telco.customer.application.handler;

import com.telco.customer.application.dto.DocumentResponse;
import com.telco.customer.application.query.ListDocumentsQuery;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.customer.infrastructure.persistence.DocumentRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Lists KYC document metadata for a customer (FR-03); unknown customer yields 404. */
@Component
public class ListDocumentsQueryHandler
        implements QueryHandler<ListDocumentsQuery, List<DocumentResponse>> {

    private final CustomerRepository customers;
    private final DocumentRepository documents;

    public ListDocumentsQueryHandler(CustomerRepository customers, DocumentRepository documents) {
        this.customers = customers;
        this.documents = documents;
    }

    @Override
    public List<DocumentResponse> handle(ListDocumentsQuery query) {
        if (!customers.existsById(query.customerId())) {
            throw new ResourceNotFoundException("customer not found: " + query.customerId());
        }
        return documents.findByCustomerId(query.customerId()).stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
