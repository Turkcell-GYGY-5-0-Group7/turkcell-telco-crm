package com.telco.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

    // ── identity-service ───────────────────────────────────────────────────────────────────

    /**
     * Provisions a real Keycloak-backed user with an initial non-temporary password (ADMIN only,
     * {@code UserController.createUser}), so the account can authenticate via ROPC immediately -
     * used to build a genuine self-service SUBSCRIBER for the identity-to-customer linkage proof
     * (Section 14.1.1/Feature 14.4), replacing the permanently-unlinkable seeded
     * {@code subscriber@telco.local} for flows that need real ownership verification.
     */
    public static Response createUser(String adminToken, String username, String email,
                                       String firstName, String lastName, String password) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "password", password);
        return auth(adminToken).body(body).post("/api/v1/users");
    }

    /** Assigns realm roles to a user (ADMIN only, {@code UserController.assignRoles}). */
    public static Response assignRoles(String adminToken, UUID userId, List<String> roleNames) {
        return auth(adminToken).body(Map.of("roleNames", roleNames)).post("/api/v1/users/{id}/roles", userId);
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

    /**
     * No {@code @PreAuthorize} on {@code CustomerDocumentController.upload}: the subscriber uploads
     * their own KYC document. The content type must be one of
     * {@code UploadDocumentCommandHandler.ALLOWED_CONTENT_TYPES} (image/jpeg, image/png,
     * application/pdf) - the handler validates the declared content type but never the byte content
     * itself (no magic-byte sniffing), so a fake payload declared as {@code application/pdf} is
     * accepted, matching what a real KYC upload's request shape looks like.
     */
    public static Response uploadKycDocument(String token, UUID customerId) {
        return RestAssured.given()
                .auth().oauth2(token)
                .contentType(ContentType.MULTIPART)
                .multiPart("type", "ID_CARD")
                .multiPart("file", "id-card.pdf", "acceptance-suite-fake-id-scan".getBytes(), "application/pdf")
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

    public record AddonCreated(UUID id, String code) {
    }

    /**
     * Creates a catalog addon (ADMIN only, {@code AddonController.create}, Feature 24.1) and
     * returns its id and code. Allowance fields are nullable and type-dependent; the addon is
     * linked to {@code applicableTariffCode} when given.
     */
    public static AddonCreated createAddon(String adminToken, BigDecimal price, String type,
                                           Long dataMb, Long voiceMinutes, Long smsCount,
                                           String applicableTariffCode) {
        String code = "ACC-ADDON-" + UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Acceptance Addon " + type);
        body.put("price", price);
        body.put("currency", "TRY");
        body.put("type", type);
        body.put("validityDays", 30);
        if (dataMb != null) {
            body.put("dataMb", dataMb);
        }
        if (voiceMinutes != null) {
            body.put("voiceMinutes", voiceMinutes);
        }
        if (smsCount != null) {
            body.put("smsCount", smsCount);
        }
        if (applicableTariffCode != null) {
            body.put("applicableTariffCodes", List.of(applicableTariffCode));
        }
        Response response = auth(adminToken).body(body).post("/api/v1/addons");
        response.then().statusCode(201);
        return new AddonCreated(UUID.fromString(response.jsonPath().getString("data.id")), code);
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

    /**
     * Places a single-item order that requests a specific campaign ({@code OrderItemRequest
     * .campaignCode}, Feature 21.3.3): order-service asks campaign-service to validate that campaign
     * for the item's tariff and, if eligible, prices the item at the discounted rate; ineligible or
     * unreachable-campaign-service outcomes leave the item at the undiscounted tariff rate
     * (fail-open, ADR-027 Decision Section 4).
     */
    public static Response createOrderWithCampaign(String token, UUID customerId, UUID tariffId,
                                                   String campaignCode, String idempotencyKey) {
        Map<String, Object> body = Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("tariffId", tariffId, "quantity", 1, "campaignCode", campaignCode)));
        return auth(token)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/orders");
    }

    /**
     * Places a NEW_LINE order bundling ADDON items with the tariff item (Sprint 24 Feature 24.3,
     * design-note D1): one {@code addon.purchased.v1} per addon fires when the order fulfills.
     */
    public static Response createOrderWithAddons(String token, UUID customerId, UUID tariffId,
                                                 List<String> addonCodes, String idempotencyKey) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("tariffId", tariffId, "quantity", 1));
        for (String addonCode : addonCodes) {
            items.add(Map.of("itemType", "ADDON", "productCode", addonCode, "quantity", 1));
        }
        Map<String, Object> body = Map.of("customerId", customerId, "items", items);
        return auth(token)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/orders");
    }

    /**
     * Places a standalone ADDON order against an existing ACTIVE subscription (Sprint 24 Feature
     * 24.3, design-note D1): no activation leg - order-service confirms AND fulfills it on
     * {@code payment.completed.v1} and publishes {@code addon.purchased.v1} per item.
     */
    public static Response createStandaloneAddonOrder(String token, UUID customerId,
                                                      List<String> addonCodes,
                                                      UUID targetSubscriptionId, String idempotencyKey) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String addonCode : addonCodes) {
            items.add(Map.of("itemType", "ADDON", "productCode", addonCode, "quantity", 1,
                    "targetSubscriptionId", targetSubscriptionId));
        }
        Map<String, Object> body = Map.of("customerId", customerId, "items", items);
        return auth(token)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/orders");
    }

    /**
     * Places a PLAN_CHANGE order (Sprint 24 Feature 24.4, design-note D2): a single TARIFF item
     * carrying the target subscription. Fulfills on {@code subscription.tariff-changed.v1}.
     */
    public static Response createPlanChangeOrder(String token, UUID customerId, UUID newTariffId,
                                                 UUID targetSubscriptionId, String idempotencyKey) {
        Map<String, Object> body = Map.of("customerId", customerId,
                "items", List.of(Map.of("tariffId", newTariffId, "quantity", 1,
                        "targetSubscriptionId", targetSubscriptionId)));
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
     * Ownership is enforced against the caller's resolved {@code customerId} claim (Section 14.1.1
     * linkage, Feature 14.4 - {@code GetSubscriptionsByCustomerQueryHandler}), not the raw JWT
     * subject. A real self-service SUBSCRIBER (see {@link SelfServiceSubscriber}) whose
     * {@code customerId} matches the query param passes this check directly; ADMIN still bypasses
     * ownership entirely, so both token kinds work here.
     */
    public static Response getSubscriptionsByCustomer(String token, UUID customerId) {
        return auth(token).queryParam("customerId", customerId).get("/api/v1/subscriptions");
    }

    /** Same resolved-{@code customerId} ownership check as {@link #getSubscriptionsByCustomer}. */
    public static Response getSubscription(String token, UUID subscriptionId) {
        return auth(token).get("/api/v1/subscriptions/{id}", subscriptionId);
    }

    /** Terminates a subscription ({@code SubscriptionController}); releases its MSISDN. */
    public static Response terminateSubscription(String token, UUID subscriptionId) {
        return auth(token).post("/api/v1/subscriptions/{id}/terminate", subscriptionId);
    }

    // ── usage-service ─────────────────────────────────────────────────────────────────────

    /** Resolved-{@code customerId} ownership check (see {@link #getSubscriptionsByCustomer}). */
    public static Response getQuota(String token, UUID subscriptionId) {
        return auth(token).get("/api/v1/usage/subscriptions/{id}/quota", subscriptionId);
    }

    /** Resolved-{@code customerId} ownership check (see {@link #getSubscriptionsByCustomer}). */
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

    /** Resolved-{@code customerId} ownership check (see {@link #getSubscriptionsByCustomer}). */
    public static Response getInvoices(String token, UUID customerId) {
        return auth(token).queryParam("customerId", customerId).get("/api/v1/invoices");
    }

    // ── notification-service ──────────────────────────────────────────────────────────────

    /**
     * {@code NotificationController.history} allows
     * {@code hasRole('ADMIN') or #userId == @currentUserProvider.currentUser().customerId()} - a real
     * self-service SUBSCRIBER's resolved {@code customerId} claim now satisfies this directly
     * (Section 14.1.1 linkage, Feature 14.4), not just ADMIN.
     */
    public static Response getNotificationHistory(String token, String userId) {
        return auth(token).get("/api/v1/notifications/users/{userId}/history", userId);
    }
}
