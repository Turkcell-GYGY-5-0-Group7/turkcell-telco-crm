package com.telco.customer.application.query;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

/** Lists customers with offset pagination (PII masked). Excludes soft-deleted customers. */
public record ListCustomersQuery(int page, int size) implements Query<PageResult<CustomerResponse>> {
}
