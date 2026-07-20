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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Sprint 24 Feature 24.8 - addon bundled into onboarding (FR-09, FR-22, design-note D1/D3).
 *
 * <p>Onboarding order with one TARIFF item plus one bundled DATA addon -&gt; saga fulfills the
 * order -&gt; one {@code addon.purchased.v1} per addon item (order-service
 * {@code FulfillOrderCommandHandler} / {@code AddonPurchaseEventPublisher}) -&gt; usage-service
 * tops up the freshly provisioned quota ({@code TopUpQuotaCommandHandler}, transient-retry safe
 * against the provisioning race) -&gt; billing-service records the charge and the next bill run
 * carries exactly one {@code Addon: ...} invoice line ({@code BillRunBatchProcessor},
 * {@code addon_charge_records.billed} flips in the bill-run transaction).
 *
 * <p>Authentication follows the AC-01 pattern: catalog management (tariff + addon creation) and
 * the bill-run trigger are ADMIN; ordering and every read use the subscriber's own linked token.
 */
@DisplayName("Sprint 24: onboarding with a bundled addon")
class AddonOnboardingAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("199.90");
    private static final BigDecimal ADDON_PRICE = new BigDecimal("15.00");
    private static final int TARIFF_DATA_MB = 1024;
    private static final long ADDON_DATA_MB = 5120L;

    @Test
    @DisplayName("bundled addon tops up quota and is billed as one invoice line")
    void bundledAddonTopsUpQuotaAndIsBilledOnce() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        String subscriberToken = subscriber.initialToken();

        UUID customerId = OnboardingSteps.registerAndApproveCustomer(subscriberToken, adminToken);
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, MONTHLY_FEE, TARIFF_DATA_MB);
        GatewayApi.AddonCreated addon = GatewayApi.createAddon(
                adminToken, ADDON_PRICE, "DATA", ADDON_DATA_MB, null, null, tariff.code());

        Response orderResponse = GatewayApi.createOrderWithAddons(
                subscriberToken, customerId, tariff.id(), List.of(addon.code()),
                UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.orderType", equalTo("NEW_LINE"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        // Order total = tariff fee + addon price (both snapshotted at creation).
        assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.totalAmount")))
                .isEqualByComparingTo(MONTHLY_FEE.add(ADDON_PRICE));

        await("order reaches FULFILLED (payment completed, subscription activated)")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(subscriberToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                });

        String linkedToken = subscriber.awaitLinkedToken(customerId);
        Response subscriptions = GatewayApi.getSubscriptionsByCustomer(linkedToken, customerId);
        subscriptions.then().statusCode(200);
        UUID subscriptionId = UUID.fromString(subscriptions.jsonPath().getString("data.content[0].id"));

        // Quota = tariff allowance + addon delta (top-up may lag fulfillment: it rides
        // addon.events and retries transiently until activation provisioning has landed).
        await("quota includes the addon's data delta")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response quota = GatewayApi.getQuota(linkedToken, subscriptionId);
                    quota.then().statusCode(200);
                    assertThat(quota.jsonPath().getLong("data.mbTotal"))
                            .isEqualTo(TARIFF_DATA_MB + ADDON_DATA_MB);
                    assertThat(quota.jsonPath().getLong("data.mbRemaining"))
                            .isEqualTo(TARIFF_DATA_MB + ADDON_DATA_MB);
                });

        // Next bill run bills the addon exactly once (FR-22, design-note D3).
        Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();
        await("bill run produces an invoice carrying one addon line")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    GatewayApi.triggerBillRun(adminToken, periodStart, periodEnd).then().statusCode(202);
                    Response invoices = GatewayApi.getInvoices(linkedToken, customerId);
                    invoices.then().statusCode(200);
                    List<Map<String, Object>> content = invoices.jsonPath().getList("data.content");
                    assertThat(content).isNotEmpty();
                    List<Map<String, Object>> lines =
                            invoices.jsonPath().getList("data.content[0].lines");
                    assertThat(lines)
                            .filteredOn(l -> l.get("description").toString().startsWith("Addon:"))
                            .hasSize(1)
                            .allSatisfy(l -> {
                                assertThat(l.get("description").toString()).contains(addon.code());
                                assertThat(new BigDecimal(l.get("lineTotal").toString()))
                                        .isEqualByComparingTo(ADDON_PRICE);
                            });
                });
    }
}
