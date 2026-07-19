package com.telco.order.application;

import com.telco.order.application.event.AddonPurchasedEvent;
import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderItem;
import com.telco.order.domain.model.OrderType;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AddonPurchaseEventPublisherTest {

    @Mock private OutboxService outboxService;

    private AddonPurchaseEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AddonPurchaseEventPublisher(outboxService);
    }

    @Test
    void publishes_one_event_per_addon_item_and_skips_tariff_items() {
        Order order = Order.create(UUID.randomUUID(), "idem-1", new BigDecimal("65.00"), "user-1",
                OrderType.NEW_LINE);
        order.addTariffItem(UUID.randomUUID(), "PREMIUM_V1", 1, "Premium",
                new BigDecimal("50.00"), 1, null, null, null);
        OrderItem addon = order.addAddonItem("ADDON-5GB", "Extra 5GB", "DATA", "TRY",
                new BigDecimal("15.00"), 1, null, 5120L, null, null);
        UUID activatedSubscriptionId = UUID.randomUUID();

        publisher.publishFor(order, activatedSubscriptionId);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("addon"), eq(addon.getId().toString()),
                eq("addon.purchased.v1"), payloadCaptor.capture());

        AddonPurchasedEvent event = (AddonPurchasedEvent) payloadCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(order.getId().toString());
        assertThat(event.customerId()).isEqualTo(order.getCustomerId().toString());
        assertThat(event.subscriptionId()).isEqualTo(activatedSubscriptionId.toString());
        assertThat(event.addonCode()).isEqualTo("ADDON-5GB");
        assertThat(event.addonName()).isEqualTo("Extra 5GB");
        assertThat(event.addonType()).isEqualTo("DATA");
        assertThat(event.price()).isEqualByComparingTo("15.00");
        assertThat(event.currency()).isEqualTo("TRY");
        assertThat(event.quantity()).isEqualTo(1);
        assertThat(event.allowanceDataMb()).isEqualTo(5120L);
        assertThat(event.allowanceMinutes()).isNull();
        assertThat(event.allowanceSms()).isNull();
        assertThat(event.occurredAt()).isNotBlank();
    }

    @Test
    void standalone_item_target_subscription_wins_over_activation_subscription() {
        Order order = Order.create(UUID.randomUUID(), "idem-2", new BigDecimal("15.00"), "user-1",
                OrderType.ADDON);
        UUID target = UUID.randomUUID();
        order.addAddonItem("ADDON-100SMS", "SMS 100", "SMS", "TRY",
                new BigDecimal("8.00"), 1, target, null, null, 100L);

        publisher.publishFor(order, null);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("addon"), any(), eq("addon.purchased.v1"),
                payloadCaptor.capture());
        assertThat(((AddonPurchasedEvent) payloadCaptor.getValue()).subscriptionId())
                .isEqualTo(target.toString());
    }

    @Test
    void skips_item_without_any_subscription_id_instead_of_failing_fulfillment() {
        Order order = Order.create(UUID.randomUUID(), "idem-3", new BigDecimal("15.00"), "user-1",
                OrderType.NEW_LINE);
        order.addAddonItem("ADDON-5GB", "Extra 5GB", "DATA", "TRY",
                new BigDecimal("15.00"), 1, null, 5120L, null, null);

        publisher.publishFor(order, null);

        verify(outboxService, never()).publish(any(), any(), any(), any());
    }

    @Test
    void order_without_addon_items_publishes_nothing() {
        Order order = Order.create(UUID.randomUUID(), "idem-4", new BigDecimal("50.00"), "user-1",
                OrderType.NEW_LINE);
        order.addTariffItem(UUID.randomUUID(), "PREMIUM_V1", 1, "Premium",
                new BigDecimal("50.00"), 1, null, null, null);

        publisher.publishFor(order, UUID.randomUUID());

        verifyNoInteractions(outboxService);
    }
}
