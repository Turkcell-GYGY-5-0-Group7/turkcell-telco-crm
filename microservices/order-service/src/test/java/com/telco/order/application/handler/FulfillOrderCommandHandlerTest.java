package com.telco.order.application.handler;

import com.telco.order.application.AddonPurchaseEventPublisher;
import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.FulfillOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderType;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private AuditLogWriter auditLogWriter;
    @Mock private AddonPurchaseEventPublisher addonPurchaseEventPublisher;

    private FulfillOrderCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FulfillOrderCommandHandler(orderRepository, sagaStateRepository,
                auditLogWriter, addonPurchaseEventPublisher);
    }

    @Test
    void confirmed_order_fulfills_and_publishes_addon_events_with_activation_subscription() {
        UUID orderId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-1", new BigDecimal("65.00"), "user-1",
                OrderType.NEW_LINE);
        order.confirm();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = handler.handle(
                new FulfillOrderCommand(orderId, subscriptionId.toString(), "msg-1"));

        assertThat(response.status()).isEqualTo("FULFILLED");
        verify(addonPurchaseEventPublisher).publishFor(order, subscriptionId);
    }

    @Test
    void pending_order_rethrows_as_transient_race() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-2", new BigDecimal("50.00"), "user-1",
                OrderType.NEW_LINE);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handler.handle(
                new FulfillOrderCommand(orderId, UUID.randomUUID().toString(), "msg-2")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(addonPurchaseEventPublisher);
    }

    @Test
    void already_fulfilled_order_is_a_noop_without_republishing() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-3", new BigDecimal("50.00"), "user-1",
                OrderType.NEW_LINE);
        order.confirm();
        order.fulfill();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = handler.handle(
                new FulfillOrderCommand(orderId, UUID.randomUUID().toString(), "msg-3"));

        assertThat(response.status()).isEqualTo("FULFILLED");
        verifyNoInteractions(addonPurchaseEventPublisher);
    }
}
