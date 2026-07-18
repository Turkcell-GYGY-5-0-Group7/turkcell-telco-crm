package com.telco.acceptance.bff;

import com.telco.acceptance.support.AcceptanceConfig;
import com.telco.acceptance.support.OnboardingSteps;
import com.telco.acceptance.support.SelfServiceSubscriber;
import com.telco.acceptance.support.TokenProvider;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Sprint 16 web-bff composition surface, smoke-proven live through the gateway ({@code /bff/v1/**}
 * route, ADR-022): the four GET composition endpoints return their documented UI shapes for a real,
 * fully onboarded self-service subscriber, and the surface rejects unauthenticated callers.
 *
 * <p>web-bff is a stateless JWT resource server (starter-security, {@code WebBffSecurityConfig});
 * the SvelteKit browser channel's PKCE flow only affects how the browser OBTAINS a token, not what
 * web-bff accepts - so a real ROPC-issued bearer token exercises exactly the same authentication
 * and composition paths. Responses are UI DTOs, deliberately NOT wrapped in {@code ApiResult}
 * (documented ADR-015 exception, docs/api-contracts/web-bff.md).
 *
 * <p>The fixture onboards a complete ACTIVE subscription first so every composition endpoint has
 * real data to compose (profile + subscription + usage); the invoice list is legitimately empty
 * until a bill run, so only its envelope shape is asserted, not its contents.
 */
@DisplayName("web-bff: composition surface smoke through the gateway (Sprint 16)")
class WebBffSmokeAcceptanceIT {

    private static OnboardingSteps.ActiveSubscription subscription;

    @BeforeAll
    static void onboardRealSubscriber() {
        String adminToken = TokenProvider.adminToken();
        SelfServiceSubscriber subscriber = SelfServiceSubscriber.provision(adminToken);
        subscription = OnboardingSteps.onboardActiveSubscription(
                subscriber, adminToken, new BigDecimal("149.90"), 10240);
    }

    private static Response bffGet(String token, String path) {
        return RestAssured.given()
                .baseUri(AcceptanceConfig.GATEWAY_BASE_URL)
                .auth().oauth2(token)
                .get(path);
    }

    @Test
    @DisplayName("GET /bff/v1/home composes profile, active subscriptions, and latest invoice")
    void homeComposesForOwnCustomer() {
        Response home = bffGet(subscription.subscriberToken(), "/bff/v1/home");
        home.then().statusCode(200)
                .body("profile", notNullValue())
                .body("activeSubscriptions", notNullValue());
        assertThat(home.jsonPath().getList("activeSubscriptions")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /bff/v1/onboarding/catalog returns tariffs shaped for the wizard")
    void onboardingCatalogListsTariffs() {
        Response catalog = bffGet(subscription.subscriberToken(), "/bff/v1/onboarding/catalog");
        catalog.then().statusCode(200).body("tariffs", notNullValue());
        assertThat(catalog.jsonPath().getList("tariffs")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /bff/v1/account composes profile and per-subscription usage")
    void accountComposesSubscriptionsWithUsage() {
        Response account = bffGet(subscription.subscriberToken(), "/bff/v1/account");
        account.then().statusCode(200)
                .body("profile", notNullValue())
                .body("subscriptions", notNullValue());
        assertThat(account.jsonPath().getList("subscriptions")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /bff/v1/invoices returns the paged envelope (legitimately empty pre-bill-run)")
    void invoicesReturnsPagedEnvelope() {
        Response invoices = bffGet(subscription.subscriberToken(), "/bff/v1/invoices");
        invoices.then().statusCode(200)
                .body("invoices", notNullValue())
                .body("page", notNullValue())
                .body("totalElements", notNullValue());
    }

    @Test
    @DisplayName("unauthenticated calls are rejected (starter-security, no permitAll on /bff/v1/**)")
    void unauthenticatedIsRejected() {
        RestAssured.given()
                .baseUri(AcceptanceConfig.GATEWAY_BASE_URL)
                .get("/bff/v1/home")
                .then().statusCode(401);
    }
}
