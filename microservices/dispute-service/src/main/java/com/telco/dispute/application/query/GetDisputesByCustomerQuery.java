package com.telco.dispute.application.query;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Lists disputes for a customer. A non-admin caller is always scoped to their own
 * {@code callerCustomerId} (from {@code UserContext.customerId()}) regardless of the requested
 * {@code customerId} - {@link com.telco.dispute.application.handler.GetDisputesByCustomerQueryHandler}
 * silently scopes rather than rejecting, matching order-service's list-endpoint convention.
 */
public record GetDisputesByCustomerQuery(
        UUID customerId,
        int page,
        int size,
        String callerCustomerId,
        boolean callerIsAdmin
) implements Query<PageResult<DisputeResponse>> {
}
