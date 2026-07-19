package com.telco.order.application.handler;

import com.telco.order.application.AddonPurchaseEventPublisher;
import com.telco.order.application.AuditLogWriter;
import com.telco.order.application.command.ConfirmOrderCommand;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderType;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private AuditLogWriter auditLogWriter;
    @Mock private AddonPurchaseEventPublisher addonPurchaseEventPublisher;

    private ConfirmOrderCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConfirmOrderCommandHandler(orderRepository, sagaStateRepository,
                auditLogWriter, addonPurchaseEventPublisher);
    }

    @Test
    void new_line_order_confirms_only_and_publishes_no_addon_events() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-1", new BigDecimal("50.00"), "user-1",
                OrderType.NEW_LINE);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = handler.handle(new ConfirmOrderCommand(orderId, "pay-1", "msg-1"));

        assertThat(response.status()).isEqualTo("CONFIRMED");
        verify(auditLogWriter).log(eq("ORDER_CONFIRMED"), eq("Order"), any(), any());
        verify(auditLogWriter, never()).log(eq("ORDER_FULFILLED"), any(), any(), any());
        verifyNoInteractions(addonPurchaseEventPublisher);
    }

    @Test
    void standalone_addon_order_confirms_and_fulfills_in_one_flow() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-2", new BigDecimal("15.00"), "user-1",
                OrderType.ADDON);
        order.addAddonItem("ADDON-5GB", "Extra 5GB", "DATA", "TRY", new BigDecimal("15.00"), 1,
                UUID.randomUUID(), 5120L, null, null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = handler.handle(new ConfirmOrderCommand(orderId, "pay-2", "msg-2"));

        assertThat(response.status()).isEqualTo("FULFILLED");
        verify(addonPurchaseEventPublisher).publishFor(order, null);
        verify(auditLogWriter).log(eq("ORDER_CONFIRMED"), eq("Order"), any(), any());
        verify(auditLogWriter).log(eq("ORDER_FULFILLED"), eq("Order"), any(), any());
    }

    @Test
    void already_advanced_order_is_a_noop_without_republishing() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "idem-3", new BigDecimal("15.00"), "user-1",
                OrderType.ADDON);
        order.confirm();
        order.fulfill();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = handler.handle(new ConfirmOrderCommand(orderId, "pay-3", "msg-3"));

        assertThat(response.status()).isEqualTo("FULFILLED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(addonPurchaseEventPublisher);
    }

    @Test
    void throws_resource_not_found_when_order_does_not_exist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ConfirmOrderCommand(orderId, null, "msg-4")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
