package com.telco.customer.application.query;

import com.telco.customer.application.dto.AddressResponse;
import com.telco.platform.cqrs.Query;

import java.util.List;
import java.util.UUID;

/** Lists all addresses for a customer (FR-03). */
public record ListAddressesQuery(UUID customerId) implements Query<List<AddressResponse>> {
}
