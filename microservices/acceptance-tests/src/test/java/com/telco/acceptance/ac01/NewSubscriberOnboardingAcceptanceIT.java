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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * AC-01 - New Subscriber Onboarding (happy path), built in Sprint 09.
 *
 * <p>Drives the full saga through the gateway ({@link AcceptanceConfig#GATEWAY_BASE_URL}) exactly
 * as a real client would: customer applies -> KYC document uploaded and approved -> customer
 * selects a postpaid tariff and places an order -> payment succeeds via the mock PSP -> the
 * subscription auto-activates with an MSISDN -> a welcome SMS notification is sent.
 *
 * <p>Source of truth for each assertion:
 * <ul>
 *   <li>{@code customer-service CustomerController/CustomerKycController/CustomerDocumentController}
 *       - registration, KYC document upload, KYC approval (PENDING -&gt; ACTIVE).</li>
 *   <li>{@code order-service OrderController/CreateOrderCommandHandler} - order capture
 *       (PENDING), price snapshot from product-catalog-service.</li>
 *   <li>{@code payment-service OrderCreatedEventConsumer/ChargePaymentCommandHandler} - the
 *       automatic PSP charge triggered by {@code order.created.v1} (COMPLETED on success).</li>
 *   <li>{@code subscription-service PaymentCompletedEventConsumer/ActivateSubscriptionCommandHandler}
 *       - activation with an allocated MSISDN, triggered by {@code payment.completed.v1}.</li>
 *   <li>{@code order-service SubscriptionActivatedEventConsumer} - order FULFILLED, triggered by
 *       {@code subscription.activated.v1}.</li>
 *   <li>{@code notification-service DomainEventNotificationConsumer.onSubscriptionEvent} - the
 *       WELCOME/SMS notification, dispatched with {@code userId = customerId} from the event
 *       payload.</li>
 * </ul>
 *
 * <p><b>Known flake source (not a defect in this suite):</b> the mock PSP fails 10% of charges by
 * design ({@code MockPspAdapter.FAILURE_RATE = 0.10}) with no deterministic override and no
 * sub-hour retry path. A run that hits that 10% will time out waiting for FULFILLED. See the
 * suite-level report for the recommended fix (an env-gated deterministic PSP mode for the apps/CI
 * compose profile).
 *
 * <p><b>Authentication:</b> the customer-facing steps (register, KYC document upload, place order,
 * view own order, view payment by order) authenticate as the real seeded SUBSCRIBER user
 * ({@code subscriber@telco.local}), not ADMIN - see {@link TokenProvider#subscriberToken()} and
 * {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME}. KYC approval and tariff creation remain
 * ADMIN because they are genuinely back-office actions ({@code CustomerKycController},
 * {@code TariffController}, both {@code hasRole('ADMIN')}). Subscription, quota, and notification
 * history reads also remain ADMIN in this suite - not because they are admin-only by design, but
 * because of a confirmed identity-linkage gap (see {@link GatewayApi#getSubscriptionsByCustomer}
 * javadoc): nothing links the subscriber's Keycloak subject to the randomly generated
 * {@code customerId} their profile receives at registration, so those ownership checks
 * (`customerId == JWT sub`) can never pass for a SUBSCRIBER token here.
 */
@DisplayName("AC-01: New subscriber onboarding (happy path)")
class NewSubscriberOnboardingAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("49.90");
    private static final int DATA_MB_INCLUDED = 5000;

    @Test
    @DisplayName("customer onboards, pays, is activated with an MSISDN, and receives a welcome SMS")
    void newSubscriberOnboardingSucceedsEndToEnd() {
        String subscriberToken = TokenProvider.subscriberToken();
        String adminToken = TokenProvider.adminToken();

        OnboardingSteps.ActiveSubscription result =
                OnboardingSteps.onboardActiveSubscription(subscriberToken, adminToken, MONTHLY_FEE, DATA_MB_INCLUDED);

        // Order reached FULFILLED (asserted inside onboardActiveSubscription); re-fetch once more
        // here so this test's failure message is self-contained if the precondition regresses. The
        // real subscriber who placed the order fetches it themselves (OrderController ownership:
        // caller must be the order's creator).
        Response order = GatewayApi.getOrder(subscriberToken, result.orderId());
        order.then().statusCode(200);
        assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
        assertThat(order.jsonPath().getString("data.customerId")).isEqualTo(result.customerId().toString());

        // Payment succeeded via the mock PSP (FR-25/26). GetPaymentByOrderQueryHandler has no
        // ownership check, so the subscriber can view it directly.
        Response payment = GatewayApi.getPaymentByOrder(subscriberToken, result.orderId());
        payment.then().statusCode(200);
        assertThat(payment.jsonPath().getString("data.status")).isEqualTo("COMPLETED");
        assertThat(new BigDecimal(payment.jsonPath().getString("data.amount")))
                .isEqualByComparingTo(MONTHLY_FEE);

        // Subscription is ACTIVE with an allocated Turkish MSISDN (FR-13, subscription-service
        // MsisdnAllocationService / V2__msisdn_pool_seed.sql: the 90532xxxxxxx block). Fetched with
        // ADMIN - see class javadoc for the ownership-linkage gap blocking the SUBSCRIBER token here.
        Response subscription = GatewayApi.getSubscription(adminToken, result.subscriptionId());
        subscription.then().statusCode(200);
        assertThat(subscription.jsonPath().getString("data.status")).isEqualTo("ACTIVE");
        assertThat(subscription.jsonPath().getString("data.msisdn")).matches("90532\\d{7}");
        assertThat(subscription.jsonPath().getString("data.tariffCode")).isEqualTo(result.tariffCode());

        // Quota was provisioned from the tariff's dataMbIncluded allowance (usage-service
        // SubscriptionActivatedEventConsumer / ProvisionQuotaCommandHandler). Same ownership-linkage
        // gap as above - ADMIN token used.
        Response quota = GatewayApi.getQuota(adminToken, result.subscriptionId());
        quota.then().statusCode(200);
        assertThat(quota.jsonPath().getLong("data.mbTotal")).isEqualTo(DATA_MB_INCLUDED);
        assertThat(quota.jsonPath().getLong("data.mbRemaining")).isEqualTo(DATA_MB_INCLUDED);

        // A WELCOME/SMS notification was dispatched to the customer
        // (notification-service DomainEventNotificationConsumer.onSubscriptionEvent, userId =
        // customerId from the subscription.activated.v1 payload's customerId field). ADMIN token:
        // NotificationController.history requires #userId == authentication.name otherwise, and
        // userId here is the business customerId, not the subscriber's JWT sub.
        await("welcome SMS notification recorded")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response history = GatewayApi.getNotificationHistory(adminToken, result.customerId().toString());
                    history.then().statusCode(200);
                    List<Map<String, Object>> content = history.jsonPath().getList("data.content");
                    assertThat(content)
                            .anySatisfy(n -> {
                                assertThat(n.get("templateCode")).isEqualTo("WELCOME");
                                assertThat(n.get("channel")).isEqualTo("SMS");
                            });
                });
    }
}
