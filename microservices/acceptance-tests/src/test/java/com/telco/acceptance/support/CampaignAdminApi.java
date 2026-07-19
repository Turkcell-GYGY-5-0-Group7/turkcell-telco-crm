package com.telco.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin client for campaign-service (Sprint 21), called directly on
 * {@link AcceptanceConfig#CAMPAIGN_SERVICE_BASE_URL} rather than through the gateway.
 *
 * <p><b>Why this bypasses the gateway (documented exception):</b> no gateway route exists for
 * campaign-service by explicit tech-lead ruling (Feature 21.1.3 / ADR-027 - internal
 * service-to-service surface; a route is deferred until a real admin UI needs one). The admin API
 * is still fully JWT-protected ({@code CampaignController}, {@code @PreAuthorize("hasRole('ADMIN')")}
 * on every route), so this is a transport shortcut for test fixture setup, not an authorization
 * bypass. The order under test always goes through the gateway.
 */
public final class CampaignAdminApi {

    /** Ids a scenario needs from a created campaign. */
    public record CampaignCreated(UUID id, String code) {
    }

    private CampaignAdminApi() {
    }

    /**
     * Creates a DRAFT percentage-discount campaign applicable to exactly the given tariff code
     * ({@code CreateCampaignRequest}; valid from an hour ago until tomorrow so eligibility windows
     * never race the test clock) and returns its id and code.
     */
    public static CampaignCreated createPercentageCampaign(String adminToken, String tariffCode,
                                                           BigDecimal percentage,
                                                           int perCustomerRedemptionCap) {
        String code = "ACCCAMP-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        Map<String, Object> body = Map.of(
                "code", code,
                "name", "Acceptance Campaign " + code,
                "description", "acceptance-suite campaign fixture",
                "discountType", "PERCENTAGE",
                "discountValue", percentage,
                "applicableTariffCodes", Set.of(tariffCode),
                "validFrom", Instant.now().minusSeconds(3600).toString(),
                "validTo", Instant.now().plusSeconds(86400).toString(),
                "perCustomerRedemptionCap", perCustomerRedemptionCap);
        Response response = RestAssured.given()
                .baseUri(AcceptanceConfig.CAMPAIGN_SERVICE_BASE_URL)
                .auth().oauth2(adminToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/campaigns");
        response.then().statusCode(201);
        return new CampaignCreated(UUID.fromString(response.jsonPath().getString("data.id")), code);
    }

    /** DRAFT -> ACTIVE ({@code CampaignController.activateCampaign}). */
    public static void activateCampaign(String adminToken, UUID campaignId) {
        RestAssured.given()
                .baseUri(AcceptanceConfig.CAMPAIGN_SERVICE_BASE_URL)
                .auth().oauth2(adminToken)
                .contentType(ContentType.JSON)
                .post("/api/v1/campaigns/{id}/activate", campaignId)
                .then().statusCode(200);
    }
}
