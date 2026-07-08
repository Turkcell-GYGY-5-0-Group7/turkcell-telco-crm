package com.telco.acceptance.ac03;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.CdrEventProducer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * AC-03 - Quota Exhaustion, built in Sprint 10.
 *
 * <p>CDR simulator produces usage events -&gt; usage-service decrements quota -&gt; a warning
 * notification fires at 80% consumed -&gt; an exhaustion notification fires at 100% -&gt;
 * post-exhaustion usage keeps being recorded (not blocked) and flows to billing as overage.
 *
 * <p>The tariff in this test provisions a quota of 100 allowance units (see
 * {@code Quota.decrement}: the 80% warning fires when remaining drops to {@code total/5} = 20, i.e.
 * after 80 units are consumed; exhaustion fires at remaining = 0).
 *
 * <p>Source of truth:
 * <ul>
 *   <li>{@code usage-service CdrRecordedEventConsumer/MeterCdrCommandHandler/Quota.decrement} -
 *       quota decrement, threshold/exceeded detection (exactly once per period), and overage
 *       tracking on the record itself once remaining has reached zero.</li>
 *   <li>{@code notification-service DomainEventNotificationConsumer.onQuotaEvent} - QUOTA_80_PERCENT
 *       and QUOTA_EXCEEDED SMS notifications.</li>
 *   <li>{@code usage-service AggregateUsageCommandHandler} /
 *       {@code billing-service UsageAggregatedBillingConsumer/RecordOverageCommandHandler} /
 *       {@code RunBillCommandHandler} - post-exhaustion usage aggregated as overage and billed as an
 *       invoice line.</li>
 * </ul>
 *
 * <p><b>Stale test assertion, corrected (bug found via live acceptance testing, 2026-07-06):</b>
 * this class previously asserted quota-threshold notifications land under the literal userId
 * {@code "unknown"}, documenting what it believed was a "confirmed platform gap" -
 * {@code quota.threshold-reached.v1}/{@code quota.exceeded.v1} supposedly never carrying
 * {@code customerId}. That gap no longer exists in the application: {@code usage-service}'s
 * {@code QuotaThresholdReachedEvent}/{@code QuotaExceededEvent} both already carry a
 * {@code customerId} field (populated from the owning {@code Quota}, see their own javadoc -
 * "so notification-service can route ... to the real customer instead of falling back to
 * unknown"), and the raw Kafka payload confirms it is genuinely populated. The suite's own
 * assertion had simply never been updated to match, so it was querying a bucket the real
 * notification would never land in and would have timed out (or silently matched unrelated stale
 * "unknown" entries from other, unrelated notification types) forever. Fixed to query by the
 * subscription's real customerId.
 *
 * <p><b>Authentication:</b> onboarding's customer-facing steps (register, KYC document upload,
 * place/read the order) authenticate as a real, freshly provisioned SUBSCRIBER
 * ({@link SelfServiceSubscriber}), not the permanently-unlinkable seeded
 * {@code subscriber@telco.local}. KYC approval, tariff creation, usage aggregation, and the
 * bill-run trigger stay ADMIN (genuinely back-office actions); quota/usage-history reads, invoice
 * reads, and the notification-history assertions now use the subscriber's own fresh, linked token -
 * the identity-to-customer linkage gap documented on {@link GatewayApi#getSubscriptionsByCustomer}
 * that used to force these onto ADMIN is closed (Feature 14.4).
 */
@DisplayName("AC-03: Quota exhaustion and overage")
class QuotaExhaustionAcceptanceIT {

    private static final int QUOTA_TOTAL_UNITS = 100;

    @Test
    @DisplayName("80 percent warning, 100 percent exhaustion, and post-exhaustion overage billed")
    void quotaExhaustionFlowsToBillingAsOverage() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription subscription = OnboardingSteps.onboardActiveSubscription(
                subscriber, adminToken, new BigDecimal("19.90"), QUOTA_TOTAL_UNITS);
        String linkedToken = subscription.subscriberToken();

        try (CdrEventProducer cdrProducer = new CdrEventProducer()) {

            // 80 units consumed of 100 -> remaining = 20 = total/5 -> threshold crossed exactly once.
            cdrProducer.sendCdr(subscription.subscriptionId(), "DATA", 80);
            await("quota reflects the first 80-unit CDR")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> {
                        Response quota = GatewayApi.getQuota(linkedToken, subscription.subscriptionId());
                        quota.then().statusCode(200);
                        assertThat(quota.jsonPath().getLong("data.mbRemaining")).isEqualTo(20L);
                    });

            await("80 percent warning notification recorded")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> assertNotificationRecorded(
                            linkedToken, subscription.customerId().toString(), "QUOTA_80_PERCENT"));

            // 20 more units -> remaining = 0 -> exceeded crossed exactly once.
            cdrProducer.sendCdr(subscription.subscriptionId(), "DATA", 20);
            await("quota is fully exhausted")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> {
                        Response quota = GatewayApi.getQuota(linkedToken, subscription.subscriptionId());
                        quota.then().statusCode(200);
                        assertThat(quota.jsonPath().getLong("data.mbRemaining")).isEqualTo(0L);
                    });

            await("quota exceeded notification recorded")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> assertNotificationRecorded(
                            linkedToken, subscription.customerId().toString(), "QUOTA_EXCEEDED"));

            // Post-exhaustion usage is still metered (not blocked), recorded entirely as overage.
            cdrProducer.sendCdr(subscription.subscriptionId(), "DATA", 10);
            Instant historyFrom = Instant.now().minus(1, ChronoUnit.HOURS);
            Instant historyTo = Instant.now().plus(1, ChronoUnit.HOURS);

            await("post-exhaustion CDR is recorded as overage, not dropped")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> {
                        Response history = GatewayApi.getUsageHistory(
                                linkedToken, subscription.subscriptionId(), historyFrom, historyTo);
                        history.then().statusCode(200);
                        List<Map<String, Object>> content = history.jsonPath().getList("data.content");
                        assertThat(content).hasSize(3);
                        assertThat(content).anySatisfy(item -> {
                            assertThat(item.get("overage")).isEqualTo(true);
                            assertThat(((Number) item.get("quantity")).longValue()).isEqualTo(10L);
                        });
                    });

            // The overage flows to billing: aggregate the period, then bill it, and confirm the
            // resulting invoice carries a "Data overage" line (RunBillCommandHandler.generateInvoice).
            Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS);
            Instant periodEnd = Instant.now().plus(1, ChronoUnit.HOURS);

            Response aggregate = GatewayApi.aggregateUsage(
                    adminToken, subscription.subscriptionId(), periodStart, periodEnd);
            aggregate.then().statusCode(200);
            assertThat(aggregate.jsonPath().getLong("data.dataOverageKb")).isEqualTo(10L);

            await("bill-run generates an invoice carrying the data overage line")
                    .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                    .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                    .untilAsserted(() -> {
                        GatewayApi.triggerBillRun(adminToken, periodStart, periodEnd).then().statusCode(202);
                        Response invoices = GatewayApi.getInvoices(linkedToken, subscription.customerId());
                        invoices.then().statusCode(200);
                        List<Map<String, Object>> content = invoices.jsonPath().getList("data.content");
                        assertThat(content).isNotEmpty();
                    });

            Response invoicesResponse = GatewayApi.getInvoices(linkedToken, subscription.customerId());
            List<Map<String, Object>> invoices = invoicesResponse.jsonPath().getList("data.content");
            Map<String, Object> invoice = invoices.get(0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) invoice.get("lines");
            assertThat(lines)
                    .as("invoice includes a data overage line (post-exhaustion usage billed as overage)")
                    .anySatisfy(line -> assertThat((String) line.get("description")).contains("Data overage"));
        }
    }

    private static void assertNotificationRecorded(String token, String userId, String templateCode) {
        Response history = GatewayApi.getNotificationHistory(token, userId);
        history.then().statusCode(200);
        List<Map<String, Object>> content = history.jsonPath().getList("data.content");
        assertThat(content).anySatisfy(n -> {
            assertThat(n.get("templateCode")).isEqualTo(templateCode);
            assertThat(n.get("channel")).isEqualTo("SMS");
        });
    }
}
