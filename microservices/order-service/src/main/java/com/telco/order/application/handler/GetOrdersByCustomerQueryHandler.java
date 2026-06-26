package com.telco.order.application.handler;

import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrdersByCustomerQuery;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Returns orders for a customer. ADMIN callers filter by the requested customerId; CUSTOMER callers
 * always receive only their own orders (filtered by their Keycloak userId), preventing IDOR
 * regardless of what customerId was supplied in the request path.
 */
@Component
public class GetOrdersByCustomerQueryHandler
        implements QueryHandler<GetOrdersByCustomerQuery, PageResult<OrderResponse>> {

    private final OrderRepository orderRepository;

    public GetOrdersByCustomerQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public PageResult<OrderResponse> handle(GetOrdersByCustomerQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());
        Page<OrderResponse> page;

        if (query.callerIsAdmin()) {
            page = orderRepository.findByCustomerId(query.customerId(), pageable).map(OrderResponse::from);
        } else {
            page = orderRepository.findByUserId(query.callerUserId(), pageable).map(OrderResponse::from);
        }

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
