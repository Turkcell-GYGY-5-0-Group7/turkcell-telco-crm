package com.telco.order.application.handler;

import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.CampaignServiceClient;
import com.telco.order.infrastructure.client.CampaignValidationResponse;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private CustomerServiceClient customerServiceClient;
    @Mock private ProductCatalogServiceClient productCatalogServiceClient;
    @Mock private CampaignServiceClient campaignServiceClient;
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
                customerServiceClient, productCatalogServiceClient, campaignServiceClient,
                outboxService, auditLogWriter);
        // Default: no eligible campaign for any tariff, so existing (pre-21.3) assertions about
        // undiscounted pricing keep passing unmodified. Individual tests override this stub.
        lenient().when(campaignServiceClient.validate(any(), any(), any()))
                .thenReturn(CampaignServiceClient.NOT_ELIGIBLE_SENTINEL);
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

    // --- Feature 21.3.3: campaign discount pricing ---

    @Test
    void eligible_percentage_campaign_discounts_unit_price_and_records_campaign_id() {
        UUID campaignId = UUID.randomUUID();
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("100.00"), "TRY", 1));
        when(campaignServiceClient.validate(eq(CUSTOMER_ID), eq("P-001"), any()))
                .thenReturn(new CampaignValidationResponse(true, campaignId, "PERCENTAGE", new BigDecimal("25.00"), null));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1, "SUMMER25")),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("75.00");
        assertThat(response.items().get(0).campaignId()).isEqualTo(campaignId);
        assertThat(response.items().get(0).campaignCode()).isEqualTo("SUMMER25");
        assertThat(response.totalAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void eligible_fixed_amount_campaign_discounts_unit_price_floored_at_zero() {
        UUID campaignId = UUID.randomUUID();
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("10.00"), "TRY", 1));
        when(campaignServiceClient.validate(eq(CUSTOMER_ID), eq("P-001"), any()))
                .thenReturn(new CampaignValidationResponse(true, campaignId, "FIXED_AMOUNT", new BigDecimal("15.00"), null));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("0.00");
        assertThat(response.items().get(0).campaignId()).isEqualTo(campaignId);
        // No explicit campaignCode was requested; campaign-service auto-resolved the match, so only
        // the campaignId is known to order-service (OrderItem's class javadoc).
        assertThat(response.items().get(0).campaignCode()).isNull();
    }

    @Test
    void ineligible_campaign_decision_leaves_price_undiscounted() {
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("49.99"), "TRY", 1));
        when(campaignServiceClient.validate(eq(CUSTOMER_ID), eq("P-001"), any()))
                .thenReturn(new CampaignValidationResponse(false, null, null, null, "EXPIRED"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1, "EXPIRED_CAMPAIGN")),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("49.99");
        assertThat(response.items().get(0).campaignId()).isNull();
    }

    @Test
    void campaign_service_unreachable_still_creates_order_at_undiscounted_price() {
        // CampaignServiceClient itself never throws (its own fail-open contract, proven separately by
        // CampaignServiceClientTest) - here we simulate the outage the way the real client would
        // surface it to this handler: the sentinel "not eligible" result, never an exception.
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("49.99"), "TRY", 1));
        when(campaignServiceClient.validate(any(), any(), any()))
                .thenReturn(CampaignServiceClient.NOT_ELIGIBLE_SENTINEL);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1)),
                USER_ID);

        OrderResponse response = handler.handle(command);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("49.99");
        verify(orderRepository).save(any(Order.class));
    }
}
