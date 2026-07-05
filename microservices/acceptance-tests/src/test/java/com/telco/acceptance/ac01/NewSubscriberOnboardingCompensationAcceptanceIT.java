package com.telco.acceptance.ac01;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.GatewayApi;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * AC-01 - New Subscriber Onboarding, COMPENSATION path.
 *
 * <p>Trigger: {@code subscription-service PaymentCompletedEventConsumer} treats a multi-item order
 * as a TERMINAL, pre-activation failure - "one-line MVP invariant violated" - and dispatches
 * {@code FailSubscriptionActivationCommand} with reason {@code UNSUPPORTED_MULTI_ITEM_ORDER}
 * (see that class's javadoc). This is the only failure mode reachable deterministically from the
 * public API: the other failure mode (MSISDN pool exhaustion, {@code ActivateSubscriptionCommandHandler})
 * would require draining all 1000 seeded MSISDNs first (V2__msisdn_pool_seed.sql), which is not a
 * practical, isolated acceptance fixture. Placing an order with two order items is a legitimate,
 * documented API call (order-service enforces no line-count limit on capture) that is guaranteed
 * to fail activation - so this proves the real compensation chain, not a synthetic shortcut.
 *
 * <p>Expected compensation chain, each hop backed by the cited consumer:
 * <ol>
 *   <li>{@code payment.completed.v1} still fires first (payment is charged before subscription
 *       activation is even attempted) - {@code payment-service OrderCreatedEventConsumer}.</li>
 *   <li>{@code subscription.activation-failed.v1} - {@code subscription-service
 *       PaymentCompletedEventConsumer} (multi-item order detected).</li>
 *   <li>{@code payment.refunded.v1} - {@code payment-service SubscriptionActivationFailedEventConsumer}
 *       refunds the COMPLETED payment.</li>
 *   <li>Order -&gt; CANCELLED - {@code order-service PaymentRefundedEventConsumer}
 *       ({@code CompensateOrderCommand}, reason {@code SAGA_COMPENSATION}).</li>
 * </ol>
 *
 * <p>Same mock-PSP flake caveat as the happy-path test applies to step 1 above.
 *
 * <p><b>Authentication:</b> registration, KYC document upload, and order placement/reads use the
 * real seeded SUBSCRIBER user; KYC approval and tariff creation stay ADMIN (genuinely back-office
 * actions). The final subscriptions-by-customer check stays ADMIN too, for the same
 * ownership-linkage gap documented on {@link GatewayApi#getSubscriptionsByCustomer} - see the AC-01
 * happy-path test class javadoc for the full explanation.
 */
@DisplayName("AC-01: New subscriber onboarding, activation-failure compensation path")
class NewSubscriberOnboardingCompensationAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("29.90");

    @Test
    @DisplayName("multi-item order fails activation, payment is refunded, and the order is cancelled")
    void activationFailureTriggersRefundAndOrderCancellation() {
        String subscriberToken = TokenProvider.subscriberToken();
        String adminToken = TokenProvider.adminToken();

        UUID customerId = OnboardingSteps.registerAndApproveCustomer(subscriberToken, adminToken);
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, MONTHLY_FEE, 1000);

        // Two items -> order-service accepts it (no line-count validation on capture), but
        // subscription-service's saga consumer rejects it as an unsupported multi-item order. Placed
        // by the real subscriber (customer-facing action).
        Response orderResponse = GatewayApi.createOrder(
                subscriberToken, customerId, List.of(tariff.id(), tariff.id()), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.status", org.hamcrest.Matchers.equalTo("PENDING"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        await("order compensates to CANCELLED after activation failure")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(subscriberToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("CANCELLED");
                });

        // The payment that was charged is refunded, not left COMPLETED (payment-service
        // SubscriptionActivationFailedEventConsumer -> RefundPaymentCommand). No ownership check on
        // this read, so the subscriber can view it directly.
        await("payment is refunded")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response payment = GatewayApi.getPaymentByOrder(subscriberToken, orderId);
                    payment.then().statusCode(200);
                    assertThat(payment.jsonPath().getString("data.status")).isEqualTo("REFUNDED");
                });

        // No subscription (and therefore no MSISDN) was ever created for this customer: activation
        // never proceeded past the pre-activation multi-item guard. ADMIN token - see class javadoc.
        Response subscriptions = GatewayApi.getSubscriptionsByCustomer(adminToken, customerId);
        subscriptions.then().statusCode(200);
        List<Object> content = subscriptions.jsonPath().getList("data.content");
        assertThat(content).isEmpty();
    }
}
