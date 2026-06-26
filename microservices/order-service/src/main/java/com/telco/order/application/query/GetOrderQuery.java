package com.telco.order.application.query;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Fetches a single order by its ID. Returns 404 if the order does not exist. */
public record GetOrderQuery(
        UUID orderId,
        /** Keycloak subject (JWT sub) of the authenticated caller. */
        String callerUserId,
        boolean callerIsAdmin
) implements Query<OrderResponse> {
}
