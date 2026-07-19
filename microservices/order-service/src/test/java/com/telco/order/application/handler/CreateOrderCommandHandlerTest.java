package com.telco.order.application.handler;

import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderItemType;
import com.telco.order.domain.model.SagaState;
import com.telco.order.infrastructure.client.AddonSnapshotClientResponse;
import com.telco.order.infrastructure.client.CampaignServiceClient;
import com.telco.order.infrastructure.client.CampaignValidationResponse;
import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.SubscriptionClientResponse;
import com.telco.order.infrastructure.client.SubscriptionServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.order.infrastructure.persistence.OrderRepository;
import com.telco.order.infrastructure.persistence.SagaStateRepository;
import com.telco.order.application.AuditLogWriter;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.common.exception.ValidationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock private SubscriptionServiceClient subscriptionServiceClient;
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
                subscriptionServiceClient, outboxService, auditLogWriter);
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
        // A NEW_LINE order allows exactly one TARIFF item plus bundled ADDON items (24.2 matrix),
        // so the multi-line sum is exercised as tariff + addon lines.
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("10.00"), "TRY", 1));
        when(productCatalogServiceClient.getAddonSnapshot("ADDON-5GB"))
                .thenReturn(addonSnapshot("ADDON-5GB", new BigDecimal("20.00")));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 3), addonItem("ADDON-5GB", 1, null)),
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

    // --- Sprint 24 Feature 24.2: order-kind derivation + validation matrix ---

    private static OrderItemRequest addonItem(String productCode, int quantity, UUID targetSubscriptionId) {
        return new OrderItemRequest(null, quantity, null, OrderItemType.ADDON, productCode, targetSubscriptionId);
    }

    private static AddonSnapshotClientResponse addonSnapshot(String code, BigDecimal price) {
        return new AddonSnapshotClientResponse(UUID.randomUUID(), code, code + " name", "DATA",
                price, "TRY", 30, 5120L, null, null);
    }

    private void stubHappyPersistence() {
        when(orderRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(customerServiceClient.getCustomer(CUSTOMER_ID))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        lenient().when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sagaStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SubscriptionClientResponse activeSubscription(UUID subscriptionId, String tariffCode) {
        return new SubscriptionClientResponse(subscriptionId, CUSTOMER_ID, "ACTIVE", tariffCode, 1,
                "905321234567");
    }

    @Test
    void new_line_order_with_bundled_addons_snapshots_addon_price_name_and_allowances() {
        stubHappyPersistence();
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("49.99"), "TRY", 1));
        when(productCatalogServiceClient.getAddonSnapshot("ADDON-5GB"))
                .thenReturn(new AddonSnapshotClientResponse(UUID.randomUUID(), "ADDON-5GB", "Extra 5GB",
                        "DATA", new BigDecimal("15.00"), "TRY", 30, 5120L, null, null));

        OrderResponse response = handler.handle(new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1), addonItem("ADDON-5GB", 2, null)),
                USER_ID));

        assertThat(response.orderType()).isEqualTo("NEW_LINE");
        assertThat(response.totalAmount()).isEqualByComparingTo("79.99");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).itemType()).isEqualTo("TARIFF");
        assertThat(response.items().get(1).itemType()).isEqualTo("ADDON");
        assertThat(response.items().get(1).productCode()).isEqualTo("ADDON-5GB");
        assertThat(response.items().get(1).tariffName()).isEqualTo("Extra 5GB");
        assertThat(response.items().get(1).unitPrice()).isEqualByComparingTo("15.00");
        assertThat(response.items().get(1).tariffId()).isNull();
        assertThat(response.items().get(1).targetSubscriptionId()).isNull();
        // Bundled addons never require the subscription-validation hop.
        verify(subscriptionServiceClient, never()).getSubscription(any());
        verify(outboxService).publish(eq("order"), any(), eq("order.created.v1"), any());
    }

    @Test
    void standalone_addon_order_validates_target_subscription_and_derives_addon_type() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getAddonSnapshot("ADDON-100SMS"))
                .thenReturn(new AddonSnapshotClientResponse(UUID.randomUUID(), "ADDON-100SMS", "100 SMS",
                        "SMS", new BigDecimal("9.90"), "TRY", 30, null, null, 100L));
        when(subscriptionServiceClient.getSubscription(subscriptionId))
                .thenReturn(activeSubscription(subscriptionId, "P-001"));

        OrderResponse response = handler.handle(new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("ADDON-100SMS", 1, subscriptionId)),
                USER_ID));

        assertThat(response.orderType()).isEqualTo("ADDON");
        assertThat(response.totalAmount()).isEqualByComparingTo("9.90");
        assertThat(response.items().get(0).itemType()).isEqualTo("ADDON");
        assertThat(response.items().get(0).targetSubscriptionId()).isEqualTo(subscriptionId);
        verify(productCatalogServiceClient, never()).getTariff(any());
        verify(campaignServiceClient, never()).validate(any(), any(), any());
    }

    @Test
    void plan_change_order_requires_a_different_tariff_and_derives_plan_change_type() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-002", "Premium", new BigDecimal("89.99"), "TRY", 2));
        when(subscriptionServiceClient.getSubscription(subscriptionId))
                .thenReturn(activeSubscription(subscriptionId, "P-001"));

        OrderResponse response = handler.handle(new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1, null, OrderItemType.TARIFF, null, subscriptionId)),
                USER_ID));

        assertThat(response.orderType()).isEqualTo("PLAN_CHANGE");
        assertThat(response.totalAmount()).isEqualByComparingTo("89.99");
        assertThat(response.items().get(0).itemType()).isEqualTo("TARIFF");
        assertThat(response.items().get(0).targetSubscriptionId()).isEqualTo(subscriptionId);
    }

    @Test
    void two_tariff_items_are_rejected() {
        UUID tariffId2 = UUID.randomUUID();
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1), new OrderItemRequest(tariffId2, 1)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("exactly one TARIFF item");
        verify(orderRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void tariff_item_without_tariff_id_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(null, 1)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("tariffId");
    }

    @Test
    void addon_item_without_product_code_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1), addonItem(null, 1, null)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("productCode");
    }

    @Test
    void campaign_code_on_addon_item_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1),
                        new OrderItemRequest(null, 1, "SUMMER25", OrderItemType.ADDON, "ADDON-5GB", null)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("campaignCode");
    }

    @Test
    void target_subscription_on_a_new_line_order_item_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1),
                        addonItem("ADDON-5GB", 1, UUID.randomUUID())),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("targetSubscriptionId is not allowed on NEW_LINE");
    }

    @Test
    void addon_order_without_target_subscription_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("ADDON-5GB", 1, null)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("targetSubscriptionId");
    }

    @Test
    void addon_order_with_mixed_target_subscriptions_is_rejected() {
        stubHappyPersistence();

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("ADDON-5GB", 1, UUID.randomUUID()),
                        addonItem("ADDON-100SMS", 1, UUID.randomUUID())),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("same subscription");
    }

    @Test
    void addon_order_against_inactive_subscription_is_rejected() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getAddonSnapshot("ADDON-5GB"))
                .thenReturn(addonSnapshot("ADDON-5GB", new BigDecimal("15.00")));
        when(subscriptionServiceClient.getSubscription(subscriptionId))
                .thenReturn(new SubscriptionClientResponse(subscriptionId, CUSTOMER_ID, "SUSPENDED",
                        "P-001", 1, "905321234567"));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("ADDON-5GB", 1, subscriptionId)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not ACTIVE");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void addon_order_against_another_customers_subscription_is_rejected() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getAddonSnapshot("ADDON-5GB"))
                .thenReturn(addonSnapshot("ADDON-5GB", new BigDecimal("15.00")));
        when(subscriptionServiceClient.getSubscription(subscriptionId))
                .thenReturn(new SubscriptionClientResponse(subscriptionId, UUID.randomUUID(), "ACTIVE",
                        "P-001", 1, "905321234567"));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("ADDON-5GB", 1, subscriptionId)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void plan_change_to_the_same_tariff_is_rejected() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getTariff(TARIFF_ID))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "P-001", "Basic", new BigDecimal("49.99"), "TRY", 1));
        when(subscriptionServiceClient.getSubscription(subscriptionId))
                .thenReturn(activeSubscription(subscriptionId, "P-001"));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(new OrderItemRequest(TARIFF_ID, 1, null, OrderItemType.TARIFF, null, subscriptionId)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already on tariff");
    }

    @Test
    void unknown_addon_code_propagates_not_found_and_creates_nothing() {
        UUID subscriptionId = UUID.randomUUID();
        stubHappyPersistence();
        when(productCatalogServiceClient.getAddonSnapshot("NOPE"))
                .thenThrow(new ResourceNotFoundException("Addon not found: NOPE"));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID, IDEM_KEY,
                List.of(addonItem("NOPE", 1, subscriptionId)),
                USER_ID);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOPE");
        verify(orderRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any(), any());
    }
}
