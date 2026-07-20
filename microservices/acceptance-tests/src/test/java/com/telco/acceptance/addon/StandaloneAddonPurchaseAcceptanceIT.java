package com.telco.acceptance.addon;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.GatewayApi;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.SelfServiceSubscriber;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Sprint 24 Feature 24.8 - standalone addon purchase for an existing subscription (FR-09,
 * design-note D1).
 *
 * <p>An ACTIVE subscriber places an all-ADDON order targeting their subscription -&gt; payment
 * completes -&gt; order-service confirms AND fulfills in one flow (no activation leg; saga step
 * {@code ADDON_FULFILLED}; subscription-service deliberately ignores the payment event - the
 * regression that would otherwise fire is {@code UNSUPPORTED_MULTI_ITEM_ORDER} compensation) -&gt;
 * {@code addon.purchased.v1} tops up the subscription's quota.
 */
@DisplayName("Sprint 24: standalone addon purchase")
class StandaloneAddonPurchaseAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("99.90");
    private static final BigDecimal ADDON_PRICE = new BigDecimal("8.00");
    private static final int TARIFF_DATA_MB = 1024;
    private static final long ADDON_SMS = 100L;

    @Test
    @DisplayName("standalone addon order fulfills without activation and tops up quota")
    void standaloneAddonOrderFulfillsAndTopsUpQuota() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription subscription = OnboardingSteps.onboardActiveSubscription(
                subscriber, adminToken, MONTHLY_FEE, TARIFF_DATA_MB);
        String linkedToken = subscription.subscriberToken();

        long smsTotalBefore = GatewayApi.getQuota(linkedToken, subscription.subscriptionId())
                .jsonPath().getLong("data.smsTotal");

        GatewayApi.AddonCreated addon = GatewayApi.createAddon(
                adminToken, ADDON_PRICE, "SMS", null, null, ADDON_SMS, subscription.tariffCode());

        Response orderResponse = GatewayApi.createStandaloneAddonOrder(
                linkedToken, subscription.customerId(), List.of(addon.code()),
                subscription.subscriptionId(), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.orderType", equalTo("ADDON"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));
        assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.totalAmount")))
                .isEqualByComparingTo(ADDON_PRICE);

        // No activation leg: payment success is terminal for an ADDON order.
        await("standalone addon order reaches FULFILLED on payment alone")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(linkedToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                });

        await("quota includes the purchased SMS delta")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response quota = GatewayApi.getQuota(linkedToken, subscription.subscriptionId());
                    quota.then().statusCode(200);
                    assertThat(quota.jsonPath().getLong("data.smsTotal"))
                            .isEqualTo(smsTotalBefore + ADDON_SMS);
                    assertThat(quota.jsonPath().getLong("data.smsRemaining"))
                            .isEqualTo(smsTotalBefore + ADDON_SMS);
                });

        // The subscription itself is untouched: still exactly one, still on the original tariff.
        Response subscriptions = GatewayApi.getSubscriptionsByCustomer(
                linkedToken, subscription.customerId());
        subscriptions.then().statusCode(200);
        List<Object> content = subscriptions.jsonPath().getList("data.content");
        assertThat(content).hasSize(1);
        assertThat(subscriptions.jsonPath().getString("data.content[0].tariffCode"))
                .isEqualTo(subscription.tariffCode());
    }
}
