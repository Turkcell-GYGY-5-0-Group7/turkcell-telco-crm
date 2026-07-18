package com.telco.acceptance.campaign;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.CampaignAdminApi;
import com.telco.acceptance.support.CampaignDb;
import com.telco.acceptance.support.GatewayApi;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.SelfServiceSubscriber;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Sprint 21 (Feature 21.3/21.4) end-to-end proof against the live stack, gateway-driven like every
 * other scenario in this suite: a real ACTIVE campaign discounts a real order's line item, and the
 * campaign redemption saga (RESERVED on {@code order.created.v1}, CONFIRMED on
 * {@code payment.completed.v1} - both via Debezium outbox -&gt; Kafka -&gt; campaign-service inbox)
 * completes for that order.
 *
 * <p>Campaign administration goes directly to campaign-service (no gateway route by tech-lead
 * ruling - see {@link CampaignAdminApi}); the redemption assertion reads
 * {@code campaign_db.campaign_redemptions} directly (no read API exists - see
 * {@link CampaignDb}). Everything else is the same real-subscriber, real-token,
 * through-the-gateway flow as AC-01.
 */
@DisplayName("Campaign: discounted order end-to-end (Sprint 21)")
class CampaignDiscountedOrderAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("100.00");
    private static final BigDecimal DISCOUNT_PERCENTAGE = new BigDecimal("25.00");
    private static final BigDecimal DISCOUNTED_PRICE = new BigDecimal("75.00");

    @Test
    @DisplayName("ACTIVE percentage campaign discounts the order item; redemption is CONFIRMED after payment")
    void discountedOrderEndToEnd() {
        String adminToken = TokenProvider.adminToken();

        // Fixtures: a fresh tariff (ADMIN, via gateway) and a fresh ACTIVE 25% campaign scoped to
        // exactly that tariff code (ADMIN, direct - no gateway route exists for campaign-service).
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, MONTHLY_FEE, 5000);
        CampaignAdminApi.CampaignCreated campaign =
                CampaignAdminApi.createPercentageCampaign(adminToken, tariff.code(), DISCOUNT_PERCENTAGE, 1);
        CampaignAdminApi.activateCampaign(adminToken, campaign.id());

        // A real, linkable self-service subscriber registers and gets KYC-approved (AC-01 steps 1-3).
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        String subscriberToken = subscriber.initialToken();
        UUID customerId = OnboardingSteps.registerAndApproveCustomer(subscriberToken, adminToken);

        // The subscriber places a single-item order requesting the campaign, through the gateway.
        Response orderResponse = GatewayApi.createOrderWithCampaign(
                subscriberToken, customerId, tariff.id(), campaign.code(), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.status", equalTo("PENDING"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        // The synchronous validate call already priced the item: discounted unitPrice, campaign
        // identifiers recorded on the line item (Feature 21.3.3).
        assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.items[0].unitPrice")))
                .isEqualByComparingTo(DISCOUNTED_PRICE);
        assertThat(orderResponse.jsonPath().getString("data.items[0].campaignId"))
                .isEqualTo(campaign.id().toString());
        assertThat(orderResponse.jsonPath().getString("data.items[0].campaignCode"))
                .isEqualTo(campaign.code());
        assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.totalAmount")))
                .isEqualByComparingTo(DISCOUNTED_PRICE);

        // The saga completes at the discounted price (payment-service charges data.totalAmount).
        await("order reaches FULFILLED (payment completed, subscription activated)")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(subscriberToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                });

        // Redemption lifecycle (Feature 21.4): order.created.v1 reserved it, payment.completed.v1
        // confirms it - both hops cross Debezium/Kafka, so poll rather than read once.
        await("campaign redemption reaches CONFIRMED after payment.completed.v1")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> assertThat(CampaignDb.redemptionStatusForOrder(orderId))
                        .contains("CONFIRMED"));
    }
}
