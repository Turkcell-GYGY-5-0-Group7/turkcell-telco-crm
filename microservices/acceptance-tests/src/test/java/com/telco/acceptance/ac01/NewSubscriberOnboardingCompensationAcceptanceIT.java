package com.telco.acceptance.ac01;

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
 * AC-01 compensation path: activation failure refunds the payment and cancels the order.
 *
 * <p><b>Rewritten for Sprint 24 (2026-07-20 live E2E):</b> this test previously triggered
 * compensation with a two-tariff-item order, which order-service accepted (no line-count
 * validation on capture) and subscription-service later rejected as
 * {@code UNSUPPORTED_MULTI_ITEM_ORDER}. Since Feature 24.2's validation matrix, that shape is
 * rejected AT CAPTURE with 400 (a NEW_LINE order must contain exactly one TARIFF item) - the
 * failure moved to an earlier, cheaper gate, and the old trigger can no longer reach the saga.
 * The first assertion documents that gate.
 *
 * <p>The live compensation proof now drives Feature 24.4's terminal changeTariff failure: a
 * PLAN_CHANGE order is placed against an ACTIVE subscription which is then terminated BEFORE the
 * payment leg completes (payment rides order.created.v1 through Debezium, giving a comfortable
 * window). subscription-service's {@code ChangeTariffCommandHandler} finds the target no longer
 * ACTIVE, emits {@code subscription.activation-failed.v1} (reason {@code TARIFF_CHANGE_REJECTED};
 * documented event-name reuse, design-note D2), and the EXISTING compensation chain runs:
 * payment REFUNDED, order CANCELLED.
 */
@DisplayName("AC-01: activation failure compensates (refund + cancel)")
class NewSubscriberOnboardingCompensationAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("100.00");

    @Test
    @DisplayName("two-tariff order is rejected at capture since 24.2 (earlier gate)")
    void multiTariffOrderIsRejectedAtCapture() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        String subscriberToken = subscriber.initialToken();

        UUID customerId = OnboardingSteps.registerAndApproveCustomer(subscriberToken, adminToken);
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, MONTHLY_FEE, 1000);

        Response orderResponse = GatewayApi.createOrder(
                subscriberToken, customerId, List.of(tariff.id(), tariff.id()),
                UUID.randomUUID().toString());

        orderResponse.then().statusCode(400).body("success", equalTo(false));
    }

    @Test
    @DisplayName("terminal plan-change failure refunds the payment and cancels the order")
    void planChangeFailureTriggersRefundAndOrderCancellation() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription subscription = OnboardingSteps.onboardActiveSubscription(
                subscriber, adminToken, MONTHLY_FEE, 1000);
        String linkedToken = subscription.subscriberToken();

        GatewayApi.TariffCreated newTariff = GatewayApi.createTariff(
                adminToken, new BigDecimal("150.00"), 5000);

        // Place the plan-change order, then terminate the target subscription IMMEDIATELY: the
        // payment leg needs order.created.v1 to travel through Debezium first, so the direct
        // synchronous terminate call reliably lands before payment.completed.v1 is consumed.
        Response orderResponse = GatewayApi.createPlanChangeOrder(
                linkedToken, subscription.customerId(), newTariff.id(),
                subscription.subscriptionId(), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.orderType", equalTo("PLAN_CHANGE"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        GatewayApi.terminateSubscription(adminToken, subscription.subscriptionId())
                .then().statusCode(200);

        await("order compensates to CANCELLED after the tariff change is rejected")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(linkedToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("CANCELLED");
                });

        await("payment is refunded")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response payment = GatewayApi.getPaymentByOrder(linkedToken, orderId);
                    payment.then().statusCode(200);
                    assertThat(payment.jsonPath().getString("data.status")).isEqualTo("REFUNDED");
                });

        // The terminated subscription kept its original tariff: the rejected change was never applied.
        Response sub = GatewayApi.getSubscription(linkedToken, subscription.subscriptionId());
        sub.then().statusCode(200);
        assertThat(sub.jsonPath().getString("data.tariffCode")).isEqualTo(subscription.tariffCode());
        assertThat(sub.jsonPath().getString("data.status")).isEqualTo("TERMINATED");
    }
}
