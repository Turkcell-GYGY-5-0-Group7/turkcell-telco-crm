package com.telco.order.application.query;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Returns a paginated list of orders for the given customer. */
public record GetOrdersByCustomerQuery(
        UUID customerId,
        int page,
        int size,
        /** Keycloak subject (JWT sub) of the authenticated caller. */
        String callerUserId,
        boolean callerIsAdmin
) implements Query<PageResult<OrderResponse>> {
}
