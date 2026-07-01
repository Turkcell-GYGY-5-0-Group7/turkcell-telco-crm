package com.telco.customer.application.handler;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.query.GetCustomerQuery;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

/** Fetches a customer by id; a soft-deleted or unknown id yields 404 (FR-03). */
@Component
public class GetCustomerQueryHandler implements QueryHandler<GetCustomerQuery, CustomerResponse> {

    private final CustomerRepository customers;

    public GetCustomerQueryHandler(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public CustomerResponse handle(GetCustomerQuery query) {
        Customer customer = customers.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException("customer not found: " + query.id()));
        return CustomerResponse.from(customer);
    }
}
