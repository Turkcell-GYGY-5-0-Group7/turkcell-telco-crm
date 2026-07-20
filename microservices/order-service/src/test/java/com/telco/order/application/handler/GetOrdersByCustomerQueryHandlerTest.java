package com.telco.order.application.handler;

import com.telco.order.application.query.GetOrdersByCustomerQuery;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrdersByCustomerQueryHandlerTest {

    @Mock private OrderRepository orderRepository;

    private GetOrdersByCustomerQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetOrdersByCustomerQueryHandler(orderRepository);
    }

    @Test
    void admin_caller_queries_by_customer_id() {
        UUID customerId = UUID.randomUUID();
        Order order = Order.create(customerId, "key-1", new BigDecimal("49.99"), "sub-admin");
        when(orderRepository.findByCustomerId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        PageResult<?> result = handler.handle(
                new GetOrdersByCustomerQuery(customerId, 0, 10, null, "sub-admin", true));

        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findByCustomerId(eq(customerId), any());
    }

    @Test
    void customer_caller_queries_by_own_user_id_ignoring_customerId_param() {
        UUID customerId = UUID.randomUUID();
        String callerSub = "sub-customer";
        Order order = Order.create(customerId, "key-2", new BigDecimal("49.99"), callerSub);
        when(orderRepository.findByUserId(eq(callerSub), any()))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        PageResult<?> result = handler.handle(
                new GetOrdersByCustomerQuery(customerId, 0, 10, null, callerSub, false));

        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findByUserId(eq(callerSub), any());
    }

    @Test
    void returns_empty_page_when_no_orders_found() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByUserId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResult<?> result = handler.handle(
                new GetOrdersByCustomerQuery(customerId, 0, 10, null, "sub-nobody", false));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
