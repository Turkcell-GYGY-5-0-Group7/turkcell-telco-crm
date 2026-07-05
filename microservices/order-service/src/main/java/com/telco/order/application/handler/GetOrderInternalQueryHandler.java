package com.telco.order.application.handler;

import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrderInternalQuery;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * System read of a single order by its ID, with NO ownership guard. Distinct from
 * {@link GetOrderQueryHandler} (which enforces SUBSCRIBER ownership): this path is exclusively for the
 * trusted internal endpoint used by the onboarding saga and must never weaken the guarded path
 * (tech-lead ruling 1a).
 */
@Component
public class GetOrderInternalQueryHandler implements QueryHandler<GetOrderInternalQuery, OrderResponse> {

    private final OrderRepository orderRepository;

    public GetOrderInternalQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResponse handle(GetOrderInternalQuery query) {
        Order order = orderRepository.findById(query.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + query.orderId(),
                        Map.of("orderId", query.orderId().toString())));

        return OrderResponse.from(order);
    }
}
