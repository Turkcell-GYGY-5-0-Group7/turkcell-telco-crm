package com.telco.customer.application.query;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Fetches a single customer by id (PII masked). Soft-deleted customers are not found (FR-03). */
public record GetCustomerQuery(UUID id) implements Query<CustomerResponse> {
}
