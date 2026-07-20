package com.telco.acceptance.planchange;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.GatewayApi;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.SelfServiceSubscriber;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Sprint 24 Feature 24.8 - order-driven plan change (FR-09 paket degisikligi, design-note D2/D4).
 *
 * <p>An ACTIVE subscriber places a PLAN_CHANGE order for a bigger tariff -&gt; the order charges
 * the NEW tariff's monthly fee through the unchanged payment saga -&gt; subscription-service's
 * {@code payment.completed.v1} PLAN_CHANGE branch applies {@code changeTariff} and publishes
 * {@code subscription.tariff-changed.v1} -&gt; usage-service re-provisions the current period's
 * quota to the new allowances ({@code Quota.reprovision}, D4) -&gt; billing-service updates the
 * billing record -&gt; order-service fulfills the order -&gt; the next bill run charges the new
 * tariff's fee.
 */
@DisplayName("Sprint 24: plan change order")
class PlanChangeAcceptanceIT {

    private static final BigDecimal OLD_FEE = new BigDecimal("99.90");
    private static final BigDecimal NEW_FEE = new BigDecimal("249.90");
    private static final int OLD_DATA_MB = 1024;
    private static final int NEW_DATA_MB = 10240;

    @Test
    @DisplayName("plan change swaps the tariff, re-provisions quota, and bills the new fee")
    void planChangeSwapsTariffReprovisionsQuotaAndBillsNewFee() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription subscription = OnboardingSteps.onboardActiveSubscription(
                subscriber, adminToken, OLD_FEE, OLD_DATA_MB);
        String linkedToken = subscription.subscriberToken();

        GatewayApi.TariffCreated newTariff = GatewayApi.createTariff(adminToken, NEW_FEE, NEW_DATA_MB);

        Response orderResponse = GatewayApi.createPlanChangeOrder(
                linkedToken, subscription.customerId(), newTariff.id(),
                subscription.subscriptionId(), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.orderType", equalTo("PLAN_CHANGE"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        // The order charges the NEW tariff's monthly fee (design-note D2).
        assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.totalAmount")))
                .isEqualByComparingTo(NEW_FEE);

        // Fulfillment is driven by subscription.tariff-changed.v1, not activation.
        await("plan-change order reaches FULFILLED")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(linkedToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                });

        // Subscription carries the new tariff code (same subscription, no new line).
        Response sub = GatewayApi.getSubscription(linkedToken, subscription.subscriptionId());
        sub.then().statusCode(200);
        assertThat(sub.jsonPath().getString("data.tariffCode")).isEqualTo(newTariff.code());
        assertThat(sub.jsonPath().getString("data.status")).isEqualTo("ACTIVE");

        // Quota re-provisioned to the new allowances, used amounts preserved (none used here).
        await("quota re-provisioned to the new tariff's allowances")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response quota = GatewayApi.getQuota(linkedToken, subscription.subscriptionId());
                    quota.then().statusCode(200);
                    assertThat(quota.jsonPath().getLong("data.mbTotal")).isEqualTo(NEW_DATA_MB);
                    assertThat(quota.jsonPath().getLong("data.mbRemaining")).isEqualTo(NEW_DATA_MB);
                });

        // Next bill run bills the NEW tariff's monthly fee (billing record updated by
        // TariffChangedBillingConsumer before the run).
        Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();
        await("bill run invoices the new tariff's fee")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    GatewayApi.triggerBillRun(adminToken, periodStart, periodEnd).then().statusCode(202);
                    Response invoices = GatewayApi.getInvoices(linkedToken, subscription.customerId());
                    invoices.then().statusCode(200);
                    List<Map<String, Object>> content = invoices.jsonPath().getList("data.content");
                    assertThat(content).isNotEmpty();
                    List<Map<String, Object>> lines =
                            invoices.jsonPath().getList("data.content[0].lines");
                    assertThat(lines)
                            .anySatisfy(l -> {
                                assertThat(l.get("description").toString())
                                        .isEqualTo("Monthly tariff: " + newTariff.code());
                                assertThat(new BigDecimal(l.get("lineTotal").toString()))
                                        .isEqualByComparingTo(NEW_FEE);
                            });
                });
    }
}
