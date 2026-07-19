package com.telco.acceptance.campaign;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.CampaignAdminApi;
import com.telco.acceptance.support.GatewayApi;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.SelfServiceSubscriber;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Sprint 21 exit criterion, live: campaign-service unavailability must never block order creation
 * (fail-open, ADR-027 / order-service {@code CampaignServiceClient} circuit breaker). This test
 * stops the real {@code telco-campaign-service} container, places an order that requests a valid
 * ACTIVE campaign, and asserts the order is still created - at the full undiscounted price, with no
 * campaign recorded.
 *
 * <p><b>Deliberately excluded from the default sweep</b> (env-gated): tripping the connection
 * failure path can open order-service's campaign circuit breaker, and a half-open/open breaker
 * would then skip validation for any discounted-order test running in the following ~30s - a
 * cross-test pollution this suite avoids by running this class in its own invocation, after the
 * main sweep:
 * <pre>
 * CAMPAIGN_FAILOPEN_ENABLED=true mvn -f microservices/pom.xml -pl acceptance-tests -am \
 *     -Pacceptance verify -Dit.test=CampaignFailOpenAcceptanceIT
 * </pre>
 *
 * <p>The container is stopped/started via the host docker CLI ({@code ProcessBuilder}) - the same
 * host that runs this suite against the compose stack. {@link AfterAll} always restarts it and
 * waits for health; manual recovery if the JVM is killed mid-test:
 * {@code docker start telco-campaign-service}.
 */
@DisplayName("Campaign: fail-open when campaign-service is down (Sprint 21 exit criterion)")
@EnabledIfEnvironmentVariable(named = "CAMPAIGN_FAILOPEN_ENABLED", matches = "true")
class CampaignFailOpenAcceptanceIT {

    private static final String CAMPAIGN_CONTAINER = "telco-campaign-service";
    private static final BigDecimal MONTHLY_FEE = new BigDecimal("100.00");

    @AfterAll
    static void restartCampaignService() {
        docker("start", CAMPAIGN_CONTAINER);
        // ignoreExceptions: while Spring boots, the docker proxy has the port bound but nothing
        // listening behind it yet, so the poll sees Connection reset (a SocketException, which
        // untilAsserted would otherwise propagate immediately) before the first real 200.
        await("campaign-service back to UP after restart")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(2))
                .ignoreExceptions()
                .untilAsserted(() -> RestAssured.given()
                        .baseUri(AcceptanceConfig.CAMPAIGN_SERVICE_BASE_URL)
                        .get("/actuator/health")
                        .then().statusCode(200));
    }

    @Test
    @DisplayName("order still succeeds at full undiscounted price while campaign-service is down")
    void orderSucceedsUndiscountedWhileCampaignServiceDown() {
        String adminToken = TokenProvider.adminToken();

        // Fixtures created while campaign-service is still up: a tariff and a genuinely ACTIVE
        // campaign for it - so the fail-open below is attributable only to the outage, never to
        // an ineligible/unknown campaign.
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, MONTHLY_FEE, 5000);
        CampaignAdminApi.CampaignCreated campaign = CampaignAdminApi.createPercentageCampaign(
                adminToken, tariff.code(), new BigDecimal("25.00"), 1);
        CampaignAdminApi.activateCampaign(adminToken, campaign.id());

        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        String subscriberToken = subscriber.initialToken();
        UUID customerId = OnboardingSteps.registerAndApproveCustomer(subscriberToken, adminToken);

        docker("stop", CAMPAIGN_CONTAINER);
        try {
            Response orderResponse = GatewayApi.createOrderWithCampaign(
                    subscriberToken, customerId, tariff.id(), campaign.code(), UUID.randomUUID().toString());

            // Fail-open: order created despite the outage, priced at the full tariff rate, no
            // campaign recorded on the item (Feature 21.3.3, CampaignServiceClient fallback).
            orderResponse.then().statusCode(201).body("data.status", equalTo("PENDING"));
            assertThat(new BigDecimal(orderResponse.jsonPath().getString("data.items[0].unitPrice")))
                    .isEqualByComparingTo(MONTHLY_FEE);
            assertThat(orderResponse.jsonPath().getString("data.items[0].campaignId")).isNull();
            assertThat(orderResponse.jsonPath().getString("data.items[0].campaignCode")).isNull();

            // The rest of the saga does not involve campaign-service: the order still fulfills.
            UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));
            await("order reaches FULFILLED with campaign-service still down")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> {
                        Response order = GatewayApi.getOrder(subscriberToken, orderId);
                        order.then().statusCode(200);
                        assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                    });
        } finally {
            // AfterAll restarts too (and awaits health); starting here as well keeps the outage
            // window as short as possible even if a later assertion fails.
            docker("start", CAMPAIGN_CONTAINER);
        }
    }

    private static void docker(String action, String container) {
        try {
            Process process = new ProcessBuilder("docker", action, container)
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException(
                        "docker " + action + " " + container + " exited " + exit + ": " + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("docker " + action + " " + container + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker " + action + " " + container + " interrupted", e);
        }
    }
}
