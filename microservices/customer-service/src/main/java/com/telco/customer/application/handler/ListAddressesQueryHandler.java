package com.telco.customer.application.handler;

import com.telco.customer.application.dto.AddressResponse;
import com.telco.customer.application.query.ListAddressesQuery;
import com.telco.customer.infrastructure.persistence.AddressRepository;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Lists all addresses for a customer (FR-03). */
@Component
public class ListAddressesQueryHandler
        implements QueryHandler<ListAddressesQuery, List<AddressResponse>> {

    private final CustomerRepository customers;
    private final AddressRepository addresses;

    public ListAddressesQueryHandler(CustomerRepository customers, AddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @Override
    public List<AddressResponse> handle(ListAddressesQuery query) {
        if (!customers.existsById(query.customerId())) {
            throw new ResourceNotFoundException("customer not found: " + query.customerId());
        }
        return addresses.findByCustomerId(query.customerId()).stream()
                .map(AddressResponse::from)
                .toList();
    }
}
