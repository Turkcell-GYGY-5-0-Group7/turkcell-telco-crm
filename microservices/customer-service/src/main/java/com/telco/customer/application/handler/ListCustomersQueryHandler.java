package com.telco.customer.application.handler;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.query.ListCustomersQuery;
import com.telco.customer.domain.Customer;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Lists customers with offset pagination, newest first; excludes soft-deleted rows. */
@Component
public class ListCustomersQueryHandler
        implements QueryHandler<ListCustomersQuery, PageResult<CustomerResponse>> {

    private final CustomerRepository customers;

    public ListCustomersQueryHandler(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public PageResult<CustomerResponse> handle(ListCustomersQuery query) {
        Page<Customer> page = customers.findAll(
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PageResult<>(
                page.map(CustomerResponse::from).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
