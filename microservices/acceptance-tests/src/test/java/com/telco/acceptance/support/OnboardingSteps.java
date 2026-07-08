package com.telco.acceptance.support;

import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Shared AC-01 onboarding flow (customer -> KYC -> tariff -> order -> payment -> subscription),
 * factored out because AC-02 and AC-03 both need a real ACTIVE subscription as their starting
 * fixture (an invoice needs an active subscriber; a quota needs a provisioned subscription).
 * Each *AcceptanceIT class still owns and asserts its own scenario-specific steps; this class only
 * assembles the common precondition.
 *
 * <p>{@link #registerAndApproveCustomer} takes a raw {@code subscriberToken} (issued before
 * self-registration, so it never carries a {@code customerId} claim yet) and an
 * {@code adminToken}; {@link #onboardActiveSubscription} instead takes the
 * {@link SelfServiceSubscriber} itself so it can, after registration, wait for identity-service's
 * asynchronous {@code customer.registered.v1} linkage (Feature 14.4) and fetch a <em>fresh</em>
 * token carrying the resolved {@code customerId} claim before making any ownership-gated read - see
 * each {@link GatewayApi} call site's javadoc for the exact role/ownership reasoning per endpoint.
 */
public final class OnboardingSteps {

    private OnboardingSteps() {
    }

    /**
     * Every id a downstream scenario (AC-02, AC-03) needs from a completed onboarding, plus the
     * subscriber's own fresh, linked bearer token ({@code subscriberToken}) - carries the resolved
     * {@code customerId} claim (Feature 14.4) and is what every "view my own resource" read uses
     * instead of an ADMIN-token workaround.
     */
    public record ActiveSubscription(
            UUID customerId,
            UUID orderId,
            UUID subscriptionId,
            String msisdn,
            String tariffCode,
            BigDecimal monthlyFee,
            int dataMbIncluded,
            String subscriberToken) {
    }

    /**
     * AC-01 steps 1-3: register as the subscriber (PENDING) -> subscriber uploads their own KYC
     * document -> an ADMIN approves KYC (-> ACTIVE). Registration and document upload carry no
     * {@code @PreAuthorize} (any authenticated caller), so the real subscriber performs them
     * directly; approval is a back-office decision restricted to ADMIN
     * ({@code CustomerKycController}).
     */
    public static UUID registerAndApproveCustomer(String subscriberToken, String adminToken) {
        Response registered = GatewayApi.registerCustomer(
                subscriberToken, "Ada", "Acceptance", TurkishIdGenerator.next(), LocalDate.of(1990, 1, 1).toString());
        registered.then().statusCode(201);
        UUID customerId = UUID.fromString(registered.jsonPath().getString("data.id"));

        GatewayApi.uploadKycDocument(subscriberToken, customerId).then().statusCode(201);
        GatewayApi.approveKyc(adminToken, customerId).then()
                .statusCode(200)
                .body("data.status", equalTo("ACTIVE"));

        return customerId;
    }

    /**
     * Runs the complete AC-01 happy path (register -> KYC -> tariff -> single-item order -> mock
     * PSP payment -> subscription activation) and waits for the order to reach FULFILLED, i.e. the
     * saga's terminal success state (order-service {@code SubscriptionActivatedEventConsumer}).
     *
     * <p>The tariff is created by ADMIN (catalog management, {@code TariffController.createTariff},
     * {@code hasRole('ADMIN')}); the order is placed and polled by the real SUBSCRIBER
     * (customer-facing, ownership tied to whoever created it - see {@code OrderController}), proving
     * a real subscriber can place and observe their own order end to end. The resulting subscription
     * is fetched with the subscriber's own fresh, linked token (Feature 14.4 closes the
     * identity-to-customer linkage gap previously documented on
     * {@link GatewayApi#getSubscriptionsByCustomer}): once identity-service's async
     * {@code customer.registered.v1} consumer links the customer, a fresh token's resolved
     * {@code customerId} claim satisfies this ownership check directly, no ADMIN fallback needed.
     *
     * <p>Note (flagged in the suite README/report): the mock PSP
     * ({@code payment-service MockPspAdapter}) fails 10% of charges by design with no
     * deterministic override and no fast retry path (the retry scheduler's first window opens after
     * 1 hour), so this - and anything built on it - carries a small (~10%), currently unavoidable
     * flake probability tied to a real gap in the system under test, not this suite.
     */
    public static ActiveSubscription onboardActiveSubscription(SelfServiceSubscriber subscriber,
                                                               String adminToken, BigDecimal monthlyFee,
                                                               int dataMbIncluded) {
        String subscriberToken = subscriber.initialToken();
        UUID customerId = registerAndApproveCustomer(subscriberToken, adminToken);
        GatewayApi.TariffCreated tariff = GatewayApi.createTariff(adminToken, monthlyFee, dataMbIncluded);

        Response orderResponse = GatewayApi.createOrder(
                subscriberToken, customerId, List.of(tariff.id()), UUID.randomUUID().toString());
        orderResponse.then().statusCode(201).body("data.status", equalTo("PENDING"));
        UUID orderId = UUID.fromString(orderResponse.jsonPath().getString("data.id"));

        await("order reaches FULFILLED (payment completed, subscription activated)")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response order = GatewayApi.getOrder(subscriberToken, orderId);
                    order.then().statusCode(200);
                    assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
                });

        // A fresh token, fetched only after the customer is registered, carries the resolved
        // customerId claim once identity-service's async customer.registered.v1 linkage consumer
        // completes (Feature 14.4) - this is the token every ownership-gated read from here on uses,
        // including the subscriptions-by-customer lookup immediately below, closing the
        // identity-to-customer linkage gap that used to force this call onto an ADMIN token.
        String linkedToken = subscriber.awaitLinkedToken(customerId);

        Response subscriptions = GatewayApi.getSubscriptionsByCustomer(linkedToken, customerId);
        subscriptions.then().statusCode(200);
        UUID subscriptionId = UUID.fromString(subscriptions.jsonPath().getString("data.content[0].id"));
        String msisdn = subscriptions.jsonPath().getString("data.content[0].msisdn");

        return new ActiveSubscription(
                customerId, orderId, subscriptionId, msisdn, tariff.code(), monthlyFee, dataMbIncluded,
                linkedToken);
    }
}
