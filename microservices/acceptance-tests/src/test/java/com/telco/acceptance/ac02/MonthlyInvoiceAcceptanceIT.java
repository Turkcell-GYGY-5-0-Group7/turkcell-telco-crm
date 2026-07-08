package com.telco.acceptance.ac02;

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
 * AC-02 - Monthly Invoice, built in Sprint 11.
 *
 * <p>Covers: bill-run triggered by an admin (POST /api/v1/billing/runs) -&gt; invoice generated for
 * the active subscriber with a rendered PDF -&gt; an InvoiceGenerated email notification is sent
 * -&gt; the customer pays the invoice -&gt; the invoice transitions to PAID.
 *
 * <p>Source of truth:
 * <ul>
 *   <li>{@code billing-service BillingController/RunBillCommandHandler} - the bill-run iterates
 *       {@code SubscriberBillingRecord}s in status ACTIVE (populated by
 *       {@code RecordSubscriptionActivatedCommandHandler}, itself driven by
 *       {@code subscription.activated.v1}), generates one invoice line per active subscriber, renders
 *       and stores a PDF via {@code StorageService} (MinIO), and publishes
 *       {@code invoice.generated.v1}. The run is idempotent per (subscription, period).</li>
 *   <li>{@code notification-service DomainEventNotificationConsumer.onInvoiceEvent} - the
 *       INVOICE_GENERATED/EMAIL notification.</li>
 *   <li>{@code payment-service PaymentController.charge/ChargePaymentCommandHandler} - the
 *       invoice-settling charge, now carrying an optional {@code invoiceId} through to
 *       {@code payment.completed.v1}/{@code payment.failed.v1}.</li>
 *   <li>{@code billing-service PaymentCompletedBillingConsumer/MarkInvoicePaidCommandHandler} -
 *       marks the invoice PAID and publishes {@code invoice.paid.v1} once a completed payment
 *       carrying that invoice's id is observed.</li>
 * </ul>
 *
 * <p><b>"Customer pays -&gt; InvoicePaid" (Section 14.2), previously unreachable, now closed:</b>
 * an event-integration agent added an optional {@code invoiceId} to
 * {@code ChargePaymentRequest}/{@code ChargePaymentCommand} and to
 * {@code PaymentCompletedEvent}/{@code PaymentFailedEvent} (see
 * {@code docs/api-contracts/payment-service.md}, request-body table and Notes section).
 * {@code billing-service PaymentCompletedBillingConsumer} - gated on
 * {@code payload.invoiceId() != null} - now fires {@code MarkInvoicePaidCommand} when that field is
 * populated, transitioning the invoice to {@code PAID} and publishing {@code invoice.paid.v1}. This
 * test exercises that full loop: it charges a payment referencing the generated invoice's id and
 * asserts the invoice reaches {@code PAID}. The charge itself is ADMIN-only
 * ({@code PaymentController.charge}, {@code hasRole('ADMIN')}) - there is still no customer-facing
 * "pay my invoice" endpoint in the MVP, only this manual/admin charge path, so ADMIN is the correct
 * and only token for that specific call.
 *
 * <p><b>Authentication:</b> onboarding's customer-facing steps (register, KYC document upload,
 * place/read the order) authenticate as a real, freshly provisioned SUBSCRIBER
 * ({@link SelfServiceSubscriber}), not the permanently-unlinkable seeded
 * {@code subscriber@telco.local}. KYC approval, tariff creation, the bill-run trigger, and the
 * invoice-settling charge stay on the ADMIN token (genuinely back-office/admin-only actions); every
 * {@code customerId}-keyed read (invoices, notification history) now uses the subscriber's own
 * fresh, linked token - the identity-to-customer linkage gap (Section 14.1.1 ruling) that used to
 * force these onto ADMIN is closed (Feature 14.4).
 */
@DisplayName("AC-02: Monthly invoice bill-run")
class MonthlyInvoiceAcceptanceIT {

    private static final BigDecimal MONTHLY_FEE = new BigDecimal("39.90");
    private static final int DATA_MB_INCLUDED = 2000;

    @Test
    @DisplayName("bill-run generates an invoice with a PDF, notifies the customer by email, and the paid invoice reaches PAID")
    void billRunGeneratesInvoiceAndCustomerPaymentMarksItPaid() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);

        OnboardingSteps.ActiveSubscription subscription =
                OnboardingSteps.onboardActiveSubscription(subscriber, adminToken, MONTHLY_FEE, DATA_MB_INCLUDED);
        String linkedToken = subscription.subscriberToken();

        Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant periodEnd = Instant.now();

        // Idempotent per (subscription, period): safe to call repeatedly while billing-service's own
        // subscription.activated.v1 consumer catches up (it runs independently of order-service's).
        await("bill-run generates an invoice for the newly active subscriber")
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

        assertThat(invoice.get("status")).isEqualTo("ISSUED");
        assertThat(invoice.get("subscriptionId")).isEqualTo(subscription.subscriptionId().toString());
        assertThat(new BigDecimal(invoice.get("subTotal").toString()))
                .isEqualByComparingTo(MONTHLY_FEE);
        assertThat(invoice.get("pdfRef"))
                .as("invoice PDF was rendered and stored (MinIO object reference, ADR-006)")
                .isNotNull();

        await("InvoiceGenerated email notification recorded")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response history = GatewayApi.getNotificationHistory(linkedToken, subscription.customerId().toString());
                    history.then().statusCode(200);
                    List<Map<String, Object>> content = history.jsonPath().getList("data.content");
                    assertThat(content)
                            .anySatisfy(n -> {
                                assertThat(n.get("templateCode")).isEqualTo("INVOICE_GENERATED");
                                assertThat(n.get("channel")).isEqualTo("EMAIL");
                            });
                });

        // Customer pays the invoice -> payment.completed.v1 carries invoiceId -> billing-service
        // marks the invoice PAID and publishes invoice.paid.v1 (PaymentCompletedBillingConsumer,
        // MarkInvoicePaidCommandHandler). The mock PSP fails 10% of charges by design
        // (MockPspAdapter.FAILURE_RATE): retry the charge with the SAME paymentRequestId, since
        // ChargePaymentCommandHandler re-attempts a PENDING/FAILED payment under that key rather than
        // minting a new one, until it completes.
        UUID invoiceId = UUID.fromString(invoice.get("id").toString());
        BigDecimal grandTotal = new BigDecimal(invoice.get("grandTotal").toString());
        String paymentRequestId = "ac02-invoice-payment-" + invoiceId;
        UUID invoicePaymentOrderId = UUID.randomUUID();

        await("invoice-settling payment completes via the mock PSP")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response charge = GatewayApi.chargePayment(
                            adminToken, invoicePaymentOrderId, subscription.customerId(),
                            grandTotal, invoiceId, paymentRequestId);
                    charge.then().statusCode(201);
                    assertThat(charge.jsonPath().getString("data.status")).isEqualTo("COMPLETED");
                    assertThat(charge.jsonPath().getString("data.invoiceId")).isEqualTo(invoiceId.toString());
                });

        await("invoice transitions to PAID after the settling payment completes")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    Response invoicesAfterPayment = GatewayApi.getInvoices(linkedToken, subscription.customerId());
                    invoicesAfterPayment.then().statusCode(200);
                    List<Map<String, Object>> content = invoicesAfterPayment.jsonPath().getList("data.content");
                    Map<String, Object> paidInvoice = content.stream()
                            .filter(i -> invoiceId.toString().equals(i.get("id")))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("invoice " + invoiceId + " missing from list"));
                    assertThat(paidInvoice.get("status")).isEqualTo("PAID");
                });
    }
}
