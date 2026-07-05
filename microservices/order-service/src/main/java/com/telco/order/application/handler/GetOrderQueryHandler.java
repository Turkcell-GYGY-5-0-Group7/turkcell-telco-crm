package com.telco.order.application.handler;

import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrderQuery;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Returns a single order by its ID. Enforces ownership: SUBSCRIBER callers may only see their own orders. */
@Component
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, OrderResponse> {

    private final OrderRepository orderRepository;

    public GetOrderQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResponse handle(GetOrderQuery query) {
        Order order = orderRepository.findById(query.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found: " + query.orderId(),
                        Map.of("orderId", query.orderId().toString())));

        if (!query.callerIsAdmin() && !order.getUserId().equals(query.callerUserId())) {
            throw new AccessDeniedException("Order does not belong to caller");
        }

        return OrderResponse.from(order);
    }
}
