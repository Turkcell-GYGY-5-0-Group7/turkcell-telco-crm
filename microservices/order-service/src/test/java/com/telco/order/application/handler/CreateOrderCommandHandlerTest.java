package com.telco.order.application.handler;

import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.order.application.AuditLogWriter;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private CustomerServiceClient customerServiceClient;
    @Mock private ProductCatalogServiceClient productCatalogServiceClient;
    @Mock private OutboxService outboxService;
    @Mock private AuditLogWriter auditLogWriter;

    private CreateOrderCommandHandler handler;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID TARIFF_ID   = UUID.randomUUID();
    private static final String IDEM_KEY  = "idem-abc-123";
    private static final String USER_ID   = "user-sub-001";

    @BeforeEach
    void setUp() {
        handler = new CreateOrderCommandHandler(
                orderRepository, sagaStateRepository,
                customerServiceClient, productCatalogServiceClient,
                outboxService, auditLogWriter);
    }

    @Test
    void happy_path_creates_order_saves_saga_state_and_publishes_event() {
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("49.99"), "TRY", 1));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 2)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.idempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");

        verify(orderRepository).save(any(Order.class));
        verify(sagaStateRepository).save(any(SagaState.class));
        verify(outboxService).publish(eq("order"), any(), eq("order.created.v1"), any());
    }

    @Test
    void idempotent_resubmit_returns_existing_order_without_any_side_effects() {
        Order existing = Order.create(CUSTOMER_ID, IDEM_KEY, new BigDecimal("49.99"), USER_ID);
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existing));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.idempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);

        verify(customerServiceClient, never()).getCustomer(any());
        verify(productCatalogServiceClient, never()).getTariff(any());
        verify(orderRepository, never()).save(any());
        verify(sagaStateRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void total_amount_is_sum_of_all_line_totals() {
        UUID tariffId2 = UUID.randomUUID();
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("10.00"), "TRY", 1));
        when(productCatalogServiceClient.getTariff(tariffId2))
                .thenReturn(new TariffClientResponse(tariffId2, "P-002", "Premium", new BigDecimal("20.00"), "TRY", 1));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 3), new OrderItemRequest(tariffId2, 1)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void order_response_contains_item_snapshots_from_tariff_responses() {
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Postpaid Basic", new BigDecimal("49.99"), "TRY", 1));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).tariffName()).isEqualTo("Postpaid Basic");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("49.99");
        assertThat(response.items().get(0).quantity()).isEqualTo(1);
    }
}
