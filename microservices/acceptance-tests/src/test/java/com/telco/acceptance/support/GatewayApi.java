package com.telco.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin, stateless wrapper around every gateway HTTP call this suite needs, built directly against
 * the documented request/response shapes in {@code docs/api-contracts/} and each service's own
 * controllers/DTOs (see class javadoc on each *AcceptanceIT for the specific source references).
 * Every response is the platform {@code ApiResult<T>} envelope (ADR-015); callers extract
 * {@code data} via RestAssured's JsonPath as needed rather than this class imposing one DTO shape.
 *
 * <p>All calls go through the API gateway ({@link AcceptanceConfig#GATEWAY_BASE_URL}) with a real
 * Keycloak-issued bearer token (ADR-011) - never a direct call to a domain service port.
 */
public final class GatewayApi {

    static {
        RestAssured.baseURI = AcceptanceConfig.GATEWAY_BASE_URL;
    }

    private GatewayApi() {
    }

    private static RequestSpecification auth(String token) {
        return RestAssured.given()
                .auth().oauth2(token)
                .contentType(ContentType.JSON);
    }

    // ── customer-service ──────────────────────────────────────────────────────────────────

    /** No {@code @PreAuthorize} on {@code CustomerController.register}: any authenticated caller (a SUBSCRIBER applying for themselves, in this suite) may register. */
    public static Response registerCustomer(String token, String firstName, String lastName,
                                            String identityNumber, String dateOfBirthIso) {
        Map<String, Object> body = Map.of(
                "type", "INDIVIDUAL",
                "firstName", firstName,
                "lastName", lastName,
                "identityNumber", identityNumber,
                "dateOfBirth", dateOfBirthIso);
        return auth(token).body(body).post("/api/v1/customers");
    }

    /** No {@code @PreAuthorize} on {@code CustomerDocumentController.upload}: the subscriber uploads their own KYC document. */
    public static Response uploadKycDocument(String token, UUID customerId) {
        return RestAssured.given()
                .auth().oauth2(token)
                .contentType(ContentType.MULTIPART)
                .multiPart("type", "ID_CARD")
                .multiPart("file", "id-card.txt", "acceptance-suite-fake-id-scan".getBytes(), "text/plain")
                .post("/api/v1/customers/{customerId}/documents", customerId);
    }

    /** ADMIN only ({@code CustomerKycController}, {@code hasRole('ADMIN')}): back-office KYC decision. */
    public static Response approveKyc(String token, UUID customerId) {
        return auth(token).post("/api/v1/customers/{customerId}/kyc/approve", customerId);
    }

    // ── product-catalog-service ───────────────────────────────────────────────────────────

    /** Everything a caller needs to place an order against a freshly created tariff. */
    public record TariffCreated(UUID id, String code) {
    }

    /**
     * Creates a tariff (ADMIN only, {@code TariffController.createTariff}) and returns both its real
     * UUID primary key and its human-readable code. order-service's
     * {@code ProductCatalogServiceClient.getTariff(UUID tariffId)} now calls
     * {@code GET /api/v1/tariffs/by-id/{tariffId}} (added by domain-engineer), which is a genuine
     * by-primary-key lookup - so the order item's {@code tariffId} must be the tariff's real
     * {@code id}, not its {@code code}. The previous workaround (minting a UUID-shaped {@code code}
     * so a single value satisfied both order-service's field type and product-catalog's old
     * code-keyed lookup) is no longer needed and has been removed.
     */
    public static TariffCreated createTariff(String adminToken, BigDecimal monthlyFee,
                                             int dataMbIncluded) {
        String code = "ACC-" + UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "code", code,
                "name", "Acceptance Postpaid",
                "type", "POSTPAID",
                "monthlyFee", monthlyFee,
                "currency", "TRY",
                "minutesIncluded", 1000,
                "smsIncluded", 1000,
                "dataMbIncluded", dataMbIncluded,
                "targetSegment", "acceptance-suite",
                "effectiveFrom", Instant.now().toString());
        Response response = auth(adminToken).body(body).post("/api/v1/tariffs");
        response.then().statusCode(201);
        UUID id = UUID.fromString(response.jsonPath().getString("data.id"));
        return new TariffCreated(id, code);
    }

    // ── order-service ─────────────────────────────────────────────────────────────────────

    /** Places an order as the given caller (SUBSCRIBER or ADMIN); {@code tariffIds} are real tariff UUIDs. */
    public static Response createOrder(String token, UUID customerId, List<UUID> tariffIds,
                                       String idempotencyKey) {
        List<Map<String, Object>> items = tariffIds.stream()
                .<Map<String, Object>>map(tariffId -> Map.of("tariffId", tariffId, "quantity", 1))
                .toList();
        Map<String, Object> body = Map.of("customerId", customerId, "items", items);
        return auth(token)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/orders");
    }

    /** Ownership: SUBSCRIBER may fetch only an order they created themselves (OrderController). */
    public static Response getOrder(String token, UUID orderId) {
        return auth(token).get("/api/v1/orders/{orderId}", orderId);
    }

    // ── payment-service ───────────────────────────────────────────────────────────────────

    /** No ownership check in GetPaymentByOrderQueryHandler: any ADMIN or SUBSCRIBER caller may read it. */
    public static Response getPaymentByOrder(String token, UUID orderId) {
        return auth(token).get("/api/v1/payments/order/{orderId}", orderId);
    }

    /**
     * Manual/admin charge (ADMIN only, {@code PaymentController.charge}), optionally settling an
     * invoice when {@code invoiceId} is supplied - see {@code docs/api-contracts/payment-service.md}
     * Section 14.2 and {@code PaymentCompletedBillingConsumer}. {@code orderId} is not validated
     * against order-service, so a fresh UUID is a legitimate "direct invoice payment" (not tied to
     * any order-driven charge).
     */
    public static Response chargePayment(String adminToken, UUID orderId, UUID customerId,
                                         BigDecimal amount, UUID invoiceId, String paymentRequestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("customerId", customerId);
        body.put("amount", amount);
        body.put("paymentRequestId", paymentRequestId);
        body.put("invoiceId", invoiceId);
        return auth(adminToken).body(body).post("/api/v1/payments");
    }

    // ── subscription-service ──────────────────────────────────────────────────────────────

    /**
     * ADMIN only in this suite: ownership is enforced as
     * {@code customerId.toString().equals(callerUserId)} (JWT sub), and nothing links a Keycloak
     * subject to customer-service's independently generated {@code customerId} (see
     * {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME} javadoc) - a SUBSCRIBER token can never
     * pass this check for a suite-created customer, so ADMIN (which bypasses ownership) is used.
     */
    public static Response getSubscriptionsByCustomer(String token, UUID customerId) {
        return auth(token).queryParam("customerId", customerId).get("/api/v1/subscriptions");
    }

    /** Same ownership-linkage gap as {@link #getSubscriptionsByCustomer}: ADMIN only in this suite. */
    public static Response getSubscription(String token, UUID subscriptionId) {
        return auth(token).get("/api/v1/subscriptions/{id}", subscriptionId);
    }

    // ── usage-service ─────────────────────────────────────────────────────────────────────

    /** Same ownership-linkage gap (quota.customerId vs JWT sub, see AcceptanceConfig): ADMIN only. */
    public static Response getQuota(String token, UUID subscriptionId) {
        return auth(token).get("/api/v1/usage/subscriptions/{id}/quota", subscriptionId);
    }

    /** Same ownership-linkage gap: ADMIN only in this suite. */
    public static Response getUsageHistory(String token, UUID subscriptionId, Instant from, Instant to) {
        return auth(token)
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .get("/api/v1/usage/subscriptions/{id}/history", subscriptionId);
    }

    /** ADMIN only ({@code UsageController.aggregate}, {@code hasRole('ADMIN')}). */
    public static Response aggregateUsage(String token, UUID subscriptionId, Instant periodStart,
                                          Instant periodEnd) {
        Map<String, Object> body = Map.of("periodStart", periodStart.toString(), "periodEnd", periodEnd.toString());
        return auth(token).body(body)
                .post("/api/v1/usage/subscriptions/{id}/aggregate", subscriptionId);
    }

    // ── billing-service ───────────────────────────────────────────────────────────────────

    /** ADMIN only ({@code BillingController.triggerBillRun}, {@code hasRole('ADMIN')}). */
    public static Response triggerBillRun(String token, Instant periodStart, Instant periodEnd) {
        Map<String, Object> body = Map.of("periodStart", periodStart.toString(), "periodEnd", periodEnd.toString());
        return auth(token).body(body).post("/api/v1/billing/runs");
    }

    /** Same ownership-linkage gap as subscription-service reads: ADMIN only in this suite. */
    public static Response getInvoices(String token, UUID customerId) {
        return auth(token).queryParam("customerId", customerId).get("/api/v1/invoices");
    }

    // ── notification-service ──────────────────────────────────────────────────────────────

    /**
     * ADMIN only in this suite: {@code NotificationController.history} allows
     * {@code hasRole('ADMIN') or #userId == authentication.name}, and every call site here passes
     * the business {@code customerId} (or the literal {@code "unknown"} bug value, AC-03) as
     * {@code userId} - neither ever equals a SUBSCRIBER caller's JWT sub (same identity-linkage gap
     * as the other ADMIN-only reads above).
     */
    public static Response getNotificationHistory(String token, String userId) {
        return auth(token).get("/api/v1/notifications/users/{userId}/history", userId);
    }
}
