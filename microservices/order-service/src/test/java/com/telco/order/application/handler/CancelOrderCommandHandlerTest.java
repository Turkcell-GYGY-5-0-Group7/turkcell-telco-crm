package com.telco.order.application.handler;

import com.telco.order.application.command.CancelOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.order.application.AuditLogWriter;
import com.telco.platform.outbox.OutboxService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private CancelOrderCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CancelOrderCommandHandler(orderRepository, sagaStateRepository, outboxService, auditLogWriter);
    }

    @Test
    void happy_path_cancels_pending_order_and_publishes_event() {
        UUID orderId = UUID.randomUUID();
        String ownerUserId = "user-owner";
        Order order = Order.create(UUID.randomUUID(), "idem-001", new BigDecimal("50.00"), ownerUserId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = handler.handle(
                new CancelOrderCommand(orderId, "No longer needed", ownerUserId, false));

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(orderRepository).save(order);
        verify(outboxService).publish(eq("order"), any(), eq("order.cancelled.v1"), any());
    }

    @Test
    void throws_resource_not_found_when_order_does_not_exist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new CancelOrderCommand(orderId, "reason", "user-x", false)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_access_denied_when_non_admin_attempts_to_cancel_another_users_order() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-002", new BigDecimal("50.00"), "real-owner");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(
                new CancelOrderCommand(orderId, "reason", "intruder-user", false)))
                .isInstanceOf(AccessDeniedException.class);

        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void admin_can_cancel_any_order_regardless_of_owner() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-003", new BigDecimal("50.00"), "real-owner");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = handler.handle(
                new CancelOrderCommand(orderId, "admin override", "admin-user", true));

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(outboxService).publish(eq("order"), any(), eq("order.cancelled.v1"), any());
    }

    @Test
    void throws_business_rule_exception_when_cancelling_already_cancelled_order() {
        UUID orderId = UUID.randomUUID();
        String userId = "user-owner";
        Order order = Order.create(UUID.randomUUID(), "idem-004", new BigDecimal("50.00"), userId);
        order.cancel();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(
                new CancelOrderCommand(orderId, "duplicate cancel", userId, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");

        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void throws_business_rule_exception_when_cancelling_fulfilled_order() {
        UUID orderId = UUID.randomUUID();
        String userId = "user-owner";
        Order order = Order.create(UUID.randomUUID(), "idem-005", new BigDecimal("50.00"), userId);
        order.confirm();
        order.fulfill();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(
                new CancelOrderCommand(orderId, "cancel fulfilled", userId, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("FULFILLED");

        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
