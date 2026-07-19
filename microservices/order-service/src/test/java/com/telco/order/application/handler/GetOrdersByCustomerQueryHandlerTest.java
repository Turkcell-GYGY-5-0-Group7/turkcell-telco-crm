package com.telco.order.application.handler;

import com.telco.order.application.query.GetOrdersByCustomerQuery;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void absent_sort_defaults_to_created_at_desc() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByCustomerId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        handler.handle(new GetOrdersByCustomerQuery(customerId, 0, 10, null, "sub-admin", true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(customerId), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void explicit_sort_is_applied_to_the_repository_call() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByCustomerId(eq(customerId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        handler.handle(new GetOrdersByCustomerQuery(
                customerId, 0, 10, "totalAmount,asc", "sub-admin", true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerId(eq(customerId), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "totalAmount"));
    }

    @Test
    void unknown_sort_property_raises_validation_error() {
        assertThatThrownBy(() -> handler.handle(new GetOrdersByCustomerQuery(
                UUID.randomUUID(), 0, 10, "idempotencyKey,asc", "sub-admin", true)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(orderRepository);
    }
}
