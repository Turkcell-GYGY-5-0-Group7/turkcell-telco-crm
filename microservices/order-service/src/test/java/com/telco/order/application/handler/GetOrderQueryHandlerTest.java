package com.telco.order.application.handler;

import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrderQuery;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderQueryHandlerTest {

    @Mock private OrderRepository orderRepository;

    private GetOrderQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetOrderQueryHandler(orderRepository);
    }

    @Test
    void admin_caller_can_retrieve_any_order() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-001", new BigDecimal("50.00"), "user-owner");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = handler.handle(new GetOrderQuery(orderId, "admin-user", true));

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void customer_can_retrieve_their_own_order() {
        UUID orderId = UUID.randomUUID();
        String userId = "user-owner";
        Order order = Order.create(UUID.randomUUID(), "idem-002", new BigDecimal("50.00"), userId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = handler.handle(new GetOrderQuery(orderId, userId, false));

        assertThat(response).isNotNull();
        assertThat(response.idempotencyKey()).isEqualTo("idem-002");
    }

    @Test
    void throws_not_found_when_order_does_not_exist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetOrderQuery(orderId, "admin", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws_access_denied_when_customer_requests_another_users_order() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-003", new BigDecimal("50.00"), "real-owner");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(new GetOrderQuery(orderId, "intruder-user", false)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
