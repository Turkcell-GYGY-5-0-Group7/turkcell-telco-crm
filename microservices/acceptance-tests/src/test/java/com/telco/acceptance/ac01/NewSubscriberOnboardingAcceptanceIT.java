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
 * view own order, view payment by order) authenticate as a real, freshly provisioned SUBSCRIBER
 * ({@link SelfServiceSubscriber}), not the permanently-unlinkable seeded
 * {@code subscriber@telco.local} (see {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME}) and
 * not ADMIN. KYC approval and tariff creation remain ADMIN because they are genuinely back-office
 * actions ({@code CustomerKycController}, {@code TariffController}, both {@code hasRole('ADMIN')}).
 * Subscription, quota, and notification history reads use the subscriber's own <em>fresh, linked</em>
 * token ({@link SelfServiceSubscriber#awaitLinkedToken}) - the identity-to-customer linkage gap
 * (Section 14.1.1 ruling) that used to force these onto an ADMIN token is closed (Feature 14.4):
 * identity-service links {@code users.customer_id} from {@code customer.registered.v1} and pushes it
 * to Keycloak as a {@code customerId} claim, which every one of these ownership checks now resolves
 * against instead of the raw JWT subject.
 */
@DisplayName("AC-01: New subscriber onboarding (happy path)")
class NewSubscriberOnboardingAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("49.90");
    private static final int DATA_MB_INCLUDED = 5000;

    @Test
    @DisplayName("customer onboards, pays, is activated with an MSISDN, and receives a welcome SMS")
    void newSubscriberOnboardingSucceedsEndToEnd() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription result =
                OnboardingSteps.onboardActiveSubscription(subscriber, adminToken, MONTHLY_FEE, DATA_MB_INCLUDED);

        // The subscriber's own fresh, linked token (resolved customerId claim, Feature 14.4) - used
        // for every "view my own resource" read below, proving real ownership instead of falling
        // back to ADMIN.
        String linkedToken = result.subscriberToken();

        // Order reached FULFILLED (asserted inside onboardActiveSubscription); re-fetch once more
        // here so this test's failure message is self-contained if the precondition regresses. The
        // real subscriber who placed the order fetches it themselves (OrderController ownership:
        // caller must be the order's creator).
        Response order = GatewayApi.getOrder(linkedToken, result.orderId());
        order.then().statusCode(200);
        assertThat(order.jsonPath().getString("data.status")).isEqualTo("FULFILLED");
        assertThat(order.jsonPath().getString("data.customerId")).isEqualTo(result.customerId().toString());

        // Payment succeeded via the mock PSP (FR-25/26). GetPaymentByOrderQueryHandler has no
        // ownership check, so the subscriber can view it directly.
        Response payment = GatewayApi.getPaymentByOrder(linkedToken, result.orderId());
        payment.then().statusCode(200);
        assertThat(payment.jsonPath().getString("data.status")).isEqualTo("COMPLETED");
        assertThat(new BigDecimal(payment.jsonPath().getString("data.amount")))
                .isEqualByComparingTo(MONTHLY_FEE);

        // Subscription is ACTIVE with an allocated Turkish MSISDN (FR-13, subscription-service
        // MsisdnAllocationService). Fetched with the subscriber's own linked token - resolved
        // customerId claim now satisfies ownership.
        //
        // Asserts the general Turkish mobile-number shape (90 + 10 digits), not a specific block:
        // MsisdnAllocationService.allocate() only guarantees a FREE pool row, not which numeric block
        // it comes from - V2__msisdn_pool_seed.sql seeds the 0532 block, but on a long-lived local/CI
        // environment that block is a finite, shared resource that cumulative test runs eventually
        // exhaust (confirmed live: this environment's 0532 block was already fully ALLOCATED to
        // still-ACTIVE subscriptions from prior runs before this session even started, and a
        // secondary block had already been provisioned). Hardcoding the 0532 prefix here asserted an
        // implementation/environment detail, not a real behavioral contract, and was already stale
        // independent of Feature 14.4.
        Response subscription = GatewayApi.getSubscription(linkedToken, result.subscriptionId());
        subscription.then().statusCode(200);
        assertThat(subscription.jsonPath().getString("data.status")).isEqualTo("ACTIVE");
        assertThat(subscription.jsonPath().getString("data.msisdn")).matches("90\\d{10}");
        assertThat(subscription.jsonPath().getString("data.tariffCode")).isEqualTo(result.tariffCode());

        // Quota was provisioned from the tariff's dataMbIncluded allowance (usage-service
        // SubscriptionActivatedEventConsumer / ProvisionQuotaCommandHandler). Own linked token used.
        //
        // order-service (order FULFILLED, awaited above) and usage-service (quota provisioning) are
        // two independent consumer groups reacting to the same subscription.activated.v1 event with
        // no ordering guarantee between them (the same class of gap documented on
        // FulfillOrderCommandHandler's own TRANSIENT/TERMINAL split); usage-service's handler also
        // makes its own synchronous, potentially slower, outbound call (product-catalog-service's
        // allowance-snapshot lookup). Reaching FULFILLED first does not guarantee quota provisioning
        // has completed yet, so this must poll rather than assert once - matching the welcome-SMS
        // check below, which already polls for the same reason.
        await("quota provisioned from the tariff's allowance")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response quota = GatewayApi.getQuota(linkedToken, result.subscriptionId());
                    quota.then().statusCode(200);
                    assertThat(quota.jsonPath().getLong("data.mbTotal")).isEqualTo(DATA_MB_INCLUDED);
                    assertThat(quota.jsonPath().getLong("data.mbRemaining")).isEqualTo(DATA_MB_INCLUDED);
                });

        // A WELCOME/SMS notification was dispatched to the customer
        // (notification-service DomainEventNotificationConsumer.onSubscriptionEvent, userId =
        // customerId from the subscription.activated.v1 payload's customerId field).
        // NotificationController.history allows hasRole('ADMIN') or
        // #userId == @currentUserProvider.currentUser().customerId() - the subscriber's own linked
        // token now satisfies the latter directly.
        await("welcome SMS notification recorded")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response history = GatewayApi.getNotificationHistory(linkedToken, result.customerId().toString());
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
