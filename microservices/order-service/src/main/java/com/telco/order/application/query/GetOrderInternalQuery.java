package com.telco.order.application.query;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Trusted system read of a single order by its ID for the onboarding saga's synchronous hop
 * (subscription-service fetches order items + customerId during activation). Unlike
 * {@link GetOrderQuery} this carries NO caller identity and performs NO ownership guard: it is only
 * reachable via the internal-only {@code /internal/orders/{orderId}} endpoint, which the gateway
 * excludes from public traffic and the service permits without JWT (tech-lead ruling 1a). Returns
 * 404 if the order does not exist.
 */
public record GetOrderInternalQuery(UUID orderId) implements Query<OrderResponse> {
}
