package com.telco.acceptance.dispute;

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

/**
 * Sprint 22 (ADR-028) - dispute resolution's two financial-outcome paths.
 *
 * <p><b>NOT run this session - needs the full docker-compose stack (no Docker per this session's
 * constraint).</b> Written to the same standard as {@code MonthlyInvoiceAcceptanceIT}: pure
 * RestAssured against a real gateway/Keycloak, no Spring context or mocking in this module.
 *
 * <p>Source of truth:
 * <ul>
 *   <li>{@code dispute-service DisputeController}/{@code ResolveDisputeCustomerCommandHandler}/
 *       {@code ResolveDisputeMerchantCommandHandler} - publish {@code dispute.resolved-customer.v1}/
 *       {@code dispute.resolved-merchant.v1}.</li>
 *   <li>{@code payment-service DisputeResolvedCustomerPaymentConsumer} - on a {@code COMPLETED}
 *       payment carrying the resolved dispute's {@code paymentId}, dispatches the existing, unmodified
 *       {@code RefundPaymentCommand}, transitioning the payment to {@code REFUNDED} (Feature 22.5.3).
 *       {@code DisputeResolvedMerchantPaymentConsumer}/{@code billing DisputeResolvedMerchantBillingConsumer}
 *       only clear the {@code disputed} flag/hold - no financial mutation (ADR-028 Section 5
 *       provisional-hold invariant).</li>
 * </ul>
 *
 * <p>Both scenarios reuse AC-01's onboarding + AC-02's bill-run/charge steps to reach a real,
 * completed, invoice-settling payment before opening a dispute against it - the dispute-service API
 * itself never touches billing-db/payment-db directly (ADR-006); every cross-service effect here is
 * proven end to end through the real outbox -&gt; Kafka -&gt; inbox round trip.
 */
@DisplayName("ADR-028: dispute resolution financial outcomes")
class DisputeResolutionAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("29.90");
    private static final int DATA_MB_INCLUDED = 1000;

    /**
     * Onboards a subscriber, runs a bill-run, and charges the generated invoice to a real
     * {@code COMPLETED} payment - mirrors {@code MonthlyInvoiceAcceptanceIT}'s own precondition
     * exactly, since both scenarios need a genuinely paid invoice/payment pair to dispute against.
     */
    private record PaidInvoice(UUID customerId, String customerToken, UUID invoiceId, UUID paymentId,
                               BigDecimal grandTotal) {
    }

    private static PaidInvoice onboardAndPayInvoice(String adminToken) {
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        OnboardingSteps.ActiveSubscription subscription =
                OnboardingSteps.onboardActiveSubscription(subscriber, adminToken, MONTHLY_FEE, DATA_MB_INCLUDED);
        String customerToken = subscription.subscriberToken();

        Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();

        await("bill-run generates an invoice for the newly active subscriber")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    GatewayApi.triggerBillRun(adminToken, periodStart, periodEnd).then().statusCode(202);
                    Response invoices = GatewayApi.getInvoices(customerToken, subscription.customerId());
                    invoices.then().statusCode(200);
                    List<Map<String, Object>> content = invoices.jsonPath().getList("data.content");
                    assertThat(content).isNotEmpty();
                });

        Response invoicesResponse = GatewayApi.getInvoices(customerToken, subscription.customerId());
        List<Map<String, Object>> invoiceContent = invoicesResponse.jsonPath().getList("data.content");
        Map<String, Object> invoice = invoiceContent.get(0);
        UUID invoiceId = UUID.fromString(invoice.get("id").toString());
        BigDecimal grandTotal = new BigDecimal(invoice.get("grandTotal").toString());

        String paymentRequestId = "dispute-acceptance-" + invoiceId;
        UUID orderId = UUID.randomUUID();
        UUID[] paymentId = new UUID[1];

        await("invoice-settling payment completes via the mock PSP")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response charge = GatewayApi.chargePayment(
                            adminToken, orderId, subscription.customerId(), grandTotal, invoiceId, paymentRequestId);
                    charge.then().statusCode(201);
                    assertThat(charge.jsonPath().getString("data.status")).isEqualTo("COMPLETED");
                    paymentId[0] = UUID.fromString(charge.jsonPath().getString("data.id"));
                });

        await("invoice transitions to PAID after the settling payment completes")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response invoicesAfterPayment = GatewayApi.getInvoices(customerToken, subscription.customerId());
                    List<Map<String, Object>> afterPaymentContent = invoicesAfterPayment.jsonPath().getList("data.content");
                    Map<String, Object> paidInvoice = afterPaymentContent.stream()
                            .filter(i -> invoiceId.toString().equals(i.get("id")))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("invoice " + invoiceId + " missing from list"));
                    assertThat(paidInvoice.get("status")).isEqualTo("PAID");
                });

        return new PaidInvoice(subscription.customerId(), customerToken, invoiceId, paymentId[0], grandTotal);
    }

    @Test
    @DisplayName("RESOLVED_CUSTOMER on a paid invoice/payment triggers a real refund")
    void resolvedCustomerOutcomeTriggersRealRefund() {
        String adminToken = TokenProvider.adminToken();
        PaidInvoice paid = onboardAndPayInvoice(adminToken);

        Response openResponse = GatewayApi.openDispute(
                paid.customerToken(), null, paid.paymentId(), paid.customerId(),
                "SERVICE_NOT_RECEIVED", paid.grandTotal());
        openResponse.then().statusCode(201);
        UUID disputeId = UUID.fromString(openResponse.jsonPath().getString("data"));

        GatewayApi.resolveDispute(adminToken, disputeId, "CUSTOMER", paid.grandTotal())
                .then().statusCode(200);

        await("dispute reaches RESOLVED_CUSTOMER")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response dispute = GatewayApi.getDispute(paid.customerToken(), disputeId);
                    dispute.then().statusCode(200);
                    assertThat(dispute.jsonPath().getString("data.status")).isEqualTo("RESOLVED_CUSTOMER");
                });

        await("payment-service's DisputeResolvedCustomerPaymentConsumer fires a real refund")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response payment = GatewayApi.getPaymentByOrder(adminToken, paid.paymentId());
                    payment.then().statusCode(200);
                    assertThat(payment.jsonPath().getString("data.status")).isEqualTo("REFUNDED");
                });
    }

    @Test
    @DisplayName("RESOLVED_MERCHANT clears the hold with zero financial change")
    void resolvedMerchantOutcomeMakesNoFinancialChange() {
        String adminToken = TokenProvider.adminToken();
        PaidInvoice paid = onboardAndPayInvoice(adminToken);

        Response openResponse = GatewayApi.openDispute(
                paid.customerToken(), paid.invoiceId(), null, paid.customerId(),
                "DUPLICATE_CHARGE", paid.grandTotal());
        openResponse.then().statusCode(201);
        UUID disputeId = UUID.fromString(openResponse.jsonPath().getString("data"));

        GatewayApi.resolveDispute(adminToken, disputeId, "MERCHANT", null)
                .then().statusCode(200);

        await("dispute reaches RESOLVED_MERCHANT")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response dispute = GatewayApi.getDispute(paid.customerToken(), disputeId);
                    dispute.then().statusCode(200);
                    assertThat(dispute.jsonPath().getString("data.status")).isEqualTo("RESOLVED_MERCHANT");
                });

        await("billing-service's DisputeResolvedMerchantBillingConsumer clears the hold with no total change")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response invoices = GatewayApi.getInvoices(paid.customerToken(), paid.customerId());
                    List<Map<String, Object>> content = invoices.jsonPath().getList("data.content");
                    Map<String, Object> invoice = content.stream()
                            .filter(i -> paid.invoiceId().toString().equals(i.get("id")))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("invoice " + paid.invoiceId() + " missing from list"));
                    assertThat(invoice.get("disputeStatus")).isEqualTo("NONE");
                    assertThat(new BigDecimal(invoice.get("grandTotal").toString()))
                            .isEqualByComparingTo(paid.grandTotal());
                    assertThat(invoice.get("status")).isEqualTo("PAID");
                });

        Response paymentAfterResolution = GatewayApi.getPaymentByOrder(adminToken, paid.paymentId());
        paymentAfterResolution.then().statusCode(200);
        assertThat(paymentAfterResolution.jsonPath().getString("data.status")).isEqualTo("COMPLETED");
    }
}
