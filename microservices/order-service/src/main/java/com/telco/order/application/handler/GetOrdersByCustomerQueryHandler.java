package com.telco.order.application.handler;

import com.telco.order.application.SortParam;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrdersByCustomerQuery;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Returns orders for a customer. ADMIN callers filter by the requested customerId; SUBSCRIBER callers
 * always receive only their own orders (filtered by their Keycloak userId), preventing IDOR
 * regardless of what customerId was supplied in the request path.
 */
@Component
public class GetOrdersByCustomerQueryHandler
        implements QueryHandler<GetOrdersByCustomerQuery, PageResult<OrderResponse>> {

    /** Sortable order properties exposed through the API (PDF Section 12). */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "totalAmount", "status");

    private final OrderRepository orderRepository;

    public GetOrdersByCustomerQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderResponse> handle(GetOrdersByCustomerQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size(),
                SortParam.parse(query.sort(), SORTABLE_PROPERTIES));
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
