package com.telco.webbff.controller;

import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code /bff/v1} composition surface at the security boundary: each of the five
 * endpoints exists and requires a valid JWT (401 without one), and the OpenAPI document advertises all
 * five routes. The onboarding endpoints additionally assert their input validation (idempotency key,
 * body). The authenticated composition bodies (which now fan out to the gateway) are proven against a
 * mocked gateway in {@link OnboardingCompositionTest} (16.4.1) and
 * {@link AccountCompositionTest} (16.5.1); here we intentionally do not stand up a gateway, so we only
 * assert the pre-controller JWT boundary for the read endpoints.
 *
 * <p>Boots web-bff without live infrastructure (config-server and Eureka disabled), mirroring
 * {@link com.telco.webbff.WebBffApplicationTests}. Runs in Bearer mode (gateway-trust disabled) with
 * an HMAC secret so the platform {@link JwtService} bean is available to mint valid tokens - the same
 * approach notification-service's security tests use. The token proves the authenticated path reaches
 * the controller; the absence of a token proves the {@code .anyRequest().authenticated()} boundary
 * rejects with 401.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "telco.gateway.base-url=http://localhost:8080",
                "telco.platform.security.gateway-trust.enabled=false",
                "telco.platform.security.jwt.secret=d2ViLWJmZi10ZXN0LXNlY3JldC1rZXktZm9yLXRlc3RpbmctMjAyNg==",
                "telco.platform.security.jwt.issuer=telco"
        }
)
@ActiveProfiles("test")
class BffControllerTest {

    @Autowired
    private JwtService jwtService;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String token;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();
        token = jwtService.issue(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"));
    }

    private ResponseEntity<String> get(String uri, boolean authenticated) {
        RestClient.RequestHeadersSpec<?> spec = client.get().uri(uri);
        if (authenticated) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        return spec.retrieve().toEntity(String.class);
    }

    @Test
    void home_requires_jwt() {
        // The authenticated composition path (profile + subscriptions + latest invoice fan-out) is
        // proven against a mocked gateway in AccountCompositionTest; here we only assert the JWT
        // boundary, which is enforced before the controller and needs no gateway.
        assertThat(get("/bff/v1/home", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onboarding_catalog_requires_jwt() {
        // The authenticated composition path (tariffs + addons fan-out) is proven against a mocked
        // gateway in OnboardingCompositionTest; here we only assert the JWT boundary, which is enforced
        // before the controller and needs no gateway.
        assertThat(get("/bff/v1/onboarding/catalog", false).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void account_requires_jwt() {
        // Composition (profile + subscriptions + per-subscription usage) is proven in
        // AccountCompositionTest; here we assert only the JWT boundary.
        assertThat(get("/bff/v1/account", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invoices_requires_jwt() {
        // Paged composition with PDF links is proven in AccountCompositionTest; here only the boundary.
        assertThat(get("/bff/v1/invoices", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onboarding_order_requires_jwt() {
        ResponseEntity<String> unauth = client.post()
                .uri("/bff/v1/onboarding/order")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cus-1\",\"tariffCode\":\"TRF-1\"}")
                .retrieve()
                .toEntity(String.class);

        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onboarding_order_rejects_missing_idempotency_key() {
        ResponseEntity<String> response = client.post()
                .uri("/bff/v1/onboarding/order")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cus-1\",\"tariffCode\":\"TRF-1\"}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onboarding_order_rejects_invalid_body() {
        ResponseEntity<String> response = client.post()
                .uri("/bff/v1/onboarding/order")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"\",\"tariffCode\":\"\"}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void openapi_docs_are_exposed_and_cover_all_five_routes() {
        // Springdoc /v3/api-docs is permitAll in WebBffSecurityConfig (ARC-08).
        ResponseEntity<String> docs = get("/v3/api-docs", false);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("/bff/v1/home")
                .contains("/bff/v1/onboarding/catalog")
                .contains("/bff/v1/onboarding/order")
                .contains("/bff/v1/account")
                .contains("/bff/v1/invoices");
    }
}
