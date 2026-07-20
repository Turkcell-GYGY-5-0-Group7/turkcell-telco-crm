package com.telco.webbff.controller;

import com.telco.platform.starter.security.JwtService;
import com.telco.webbff.client.GatewayClient;
import com.telco.webbff.config.BearerTokenRelayInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves 16.4.1 acceptance criteria offline: the two onboarding endpoints compose real gateway routes
 * without a live stack. The gateway {@link RestClient} is replaced with one bound to a
 * {@link MockRestServiceServer}, so the full chain (controller -> {@link OnboardingCompositionService}
 * -> {@link GatewayClient} -> {@link BearerTokenRelayInterceptor}) runs against canned gateway
 * responses. Boots in Bearer mode (matching {@link BffControllerTest}) so the platform
 * {@link JwtService} mints valid tokens and the {@code .anyRequest().authenticated()} boundary applies.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "telco.gateway.base-url=http://gateway.mock",
                "telco.platform.security.gateway-trust.enabled=false",
                "telco.platform.security.jwt.secret=d2ViLWJmZi10ZXN0LXNlY3JldC1rZXktZm9yLXRlc3RpbmctMjAyNg==",
                "telco.platform.security.jwt.issuer=telco",
                // Production value is 6 MiB (configs/web-bff/application.yml); a small bound here keeps
                // the oversize-document test fast while exercising the identical code path.
                "telco.onboarding.kyc.max-document-bytes=8192"
        }
)
@ActiveProfiles("test")
class OnboardingCompositionTest {

    private static final String GATEWAY = "http://gateway.mock";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockRestServiceServer gatewayServer;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String token;

    @BeforeEach
    void setUp() {
        gatewayServer.reset();
        // JDK request factory: Apache HttpClient (Spring Boot's default when on the classpath) auto
        // re-executes idempotent requests on a 503, which would drive a spurious second inbound call
        // and defeat the downstream-503 assertion. The JDK client does not retry.
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();
        token = jwtService.issue(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"));
    }

    // --- catalog ------------------------------------------------------------------------------

    @Test
    void catalog_composes_tariffs_and_addons_in_one_response() {
        gatewayServer.expect(requestTo(containsString("/api/v1/tariffs?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(tariffPage("TRF-1", "Mega 20GB", "199.90"), MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/addons?tariffCode=TRF-1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(addonPage("ADD-1", "Extra 5GB", "29.90"), MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = get("/bff/v1/onboarding/catalog");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"tariffs\"").contains("\"addons\"")
                .contains("TRF-1").contains("Mega 20GB")
                .contains("ADD-1").contains("Extra 5GB");
        gatewayServer.verify();
    }

    @Test
    void catalog_maps_downstream_unavailable_to_503() {
        gatewayServer.expect(requestTo(containsString("/api/v1/tariffs?")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ResponseEntity<String> response = get("/bff/v1/onboarding/catalog");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        gatewayServer.verify();
    }

    // --- order --------------------------------------------------------------------------------

    @Test
    void order_registers_customer_uploads_kyc_then_places_order_forwarding_idempotency_key() {
        String key = UUID.randomUUID().toString();
        UUID customerId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        gatewayServer.expect(requestTo(endsWith("/api/v1/customers")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(apiOk("{\"id\":\"" + customerId + "\",\"status\":\"PENDING\"}"),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/customers/" + customerId + "/documents")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(apiOk("{\"id\":\"" + UUID.randomUUID() + "\",\"fileRef\":\"kyc/ref-1\"}"),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/tariffs/TRF-1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(apiOk(tariffObject(tariffId, "TRF-1", "Mega 20GB", "199.90")),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/orders")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GatewayClient.IDEMPOTENCY_KEY_HEADER, key))
                .andRespond(withSuccess(apiOk("{\"id\":\"" + orderId + "\",\"status\":\"PENDING_PAYMENT\"}"),
                        MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = postOrder(key, """
                {"tariffCode":"TRF-1",
                 "customer":{"type":"INDIVIDUAL","firstName":"Ada","lastName":"Lovelace",
                             "identityNumber":"12345678901","dateOfBirth":"1990-01-01"},
                 "kycDocument":{"type":"ID_CARD","fileName":"id.png","contentType":"image/png",
                                "content":"aGVsbG8="}}""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains(orderId.toString())
                .contains("PENDING_PAYMENT")
                .contains(key);
        gatewayServer.verify();
    }

    @Test
    void order_reuses_existing_customer_and_skips_registration() {
        String key = UUID.randomUUID().toString();
        UUID tariffId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        gatewayServer.expect(requestTo(containsString("/api/v1/tariffs/TRF-1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(apiOk(tariffObject(tariffId, "TRF-1", "Mega 20GB", "199.90")),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/orders")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GatewayClient.IDEMPOTENCY_KEY_HEADER, key))
                .andRespond(withSuccess(apiOk("{\"id\":\"" + orderId + "\",\"status\":\"PENDING_PAYMENT\"}"),
                        MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = postOrder(key,
                "{\"customerId\":\"" + UUID.randomUUID() + "\",\"tariffCode\":\"TRF-1\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(orderId.toString());
        gatewayServer.verify();
    }

    @Test
    void order_forwards_the_same_idempotency_key_on_replay() {
        String key = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();
        String body = "{\"customerId\":\"" + customerId + "\",\"tariffCode\":\"TRF-1\"}";

        // Two identical calls with the same key; the BFF forwards that key downstream on both. Whether
        // a replay returns the ORIGINAL order is enforced by order-service (idempotency), verified live
        // on the stack; the BFF's contract is deterministic forwarding, which is what we assert here.
        for (int i = 0; i < 2; i++) {
            UUID tariffId = UUID.randomUUID();
            gatewayServer.expect(requestTo(containsString("/api/v1/tariffs/TRF-1")))
                    .andRespond(withSuccess(apiOk(tariffObject(tariffId, "TRF-1", "Mega 20GB", "199.90")),
                            MediaType.APPLICATION_JSON));
            gatewayServer.expect(requestTo(containsString("/api/v1/orders")))
                    .andExpect(header(GatewayClient.IDEMPOTENCY_KEY_HEADER, key))
                    .andRespond(withSuccess(apiOk("{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"PENDING_PAYMENT\"}"),
                            MediaType.APPLICATION_JSON));
        }

        assertThat(postOrder(key, body).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postOrder(key, body).getStatusCode()).isEqualTo(HttpStatus.OK);
        gatewayServer.verify();
    }

    @Test
    void order_maps_missing_tariff_to_404() {
        String key = UUID.randomUUID().toString();
        gatewayServer.expect(requestTo(containsString("/api/v1/tariffs/TRF-X")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseEntity<String> response = postOrder(key,
                "{\"customerId\":\"" + UUID.randomUUID() + "\",\"tariffCode\":\"TRF-X\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        gatewayServer.verify();
    }

    @Test
    void order_rejects_registration_without_customer_block_with_400() {
        // No customerId and no customer block: the register-vs-reuse rule fails before any gateway call.
        ResponseEntity<String> response = postOrder(UUID.randomUUID().toString(),
                "{\"tariffCode\":\"TRF-1\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void order_rejects_an_oversize_kyc_document_with_400_before_registering_the_customer() {
        // The live defect: an oversize KYC document was forwarded to customer-service, whose multipart
        // limit rejected it AFTER the customer had already been registered - reported to the browser as
        // 503 DEPENDENCY_FAILURE (or a bare "Failed to fetch" when the connection was dropped).
        // Now it is what it always was: a CLIENT error, refused before any gateway call is made.
        // The limit is 8KB for this test (telco.onboarding.kyc.max-document-bytes below); the document
        // below decodes to 16KB.
        String content = Base64.getEncoder().encodeToString(new byte[16 * 1024]);

        ResponseEntity<String> response = postOrder(UUID.randomUUID().toString(), """
                {"tariffCode":"TRF-1",
                 "customer":{"type":"INDIVIDUAL","firstName":"Ada","lastName":"Lovelace",
                             "identityNumber":"12345678901","dateOfBirth":"1990-01-01"},
                 "kycDocument":{"type":"ID_CARD","fileName":"photo.jpg","contentType":"image/jpeg",
                                "content":"%s"}}""".formatted(content));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("VALIDATION_FAILED")
                .contains("too large")
                .contains("16384")  // the actual size, so the user is told how far over they are
                .contains("8192");  // the limit
        // No customer was registered, no document uploaded, no order placed.
        gatewayServer.verify();
    }

    @Test
    void order_requires_jwt() {
        ResponseEntity<String> response = client.post()
                .uri("/bff/v1/onboarding/order")
                .header(GatewayClient.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customerId\":\"cus-1\",\"tariffCode\":\"TRF-1\"}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ------------------------------------------------------------------------------

    private ResponseEntity<String> get(String uri) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> postOrder(String idempotencyKey, String body) {
        return client.post()
                .uri("/bff/v1/onboarding/order")
                .header("Authorization", "Bearer " + token)
                .header(GatewayClient.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
    }

    private static String apiOk(String dataJson) {
        return "{\"success\":true,\"data\":" + dataJson + "}";
    }

    private static String tariffObject(UUID id, String code, String name, String fee) {
        return "{\"id\":\"" + id + "\",\"code\":\"" + code + "\",\"name\":\"" + name
                + "\",\"type\":\"POSTPAID\",\"status\":\"ACTIVE\",\"monthlyFee\":" + fee
                + ",\"currency\":\"TRY\",\"targetSegment\":\"MASS\"}";
    }

    private static String tariffPage(String code, String name, String fee) {
        return apiOk("{\"content\":[" + tariffObject(UUID.randomUUID(), code, name, fee)
                + "],\"page\":0,\"size\":100,\"totalElements\":1,\"totalPages\":1}");
    }

    private static String addonPage(String code, String name, String price) {
        String addon = "{\"id\":\"" + UUID.randomUUID() + "\",\"code\":\"" + code + "\",\"name\":\"" + name
                + "\",\"price\":" + price + ",\"currency\":\"TRY\"}";
        return apiOk("{\"content\":[" + addon
                + "],\"page\":0,\"size\":100,\"totalElements\":1,\"totalPages\":1}");
    }

    /**
     * Replaces the gateway {@link RestClient} with one bound to a {@link MockRestServiceServer}, kept
     * on the real {@link BearerTokenRelayInterceptor} so the token-relay path is exercised. Marked
     * {@link Primary} so {@link GatewayClient} injects this instance instead of the production bean.
     */
    @TestConfiguration
    static class MockGatewayConfig {

        @Bean
        RestClient.Builder gatewayRestClientBuilder(BearerTokenRelayInterceptor interceptor) {
            return RestClient.builder().baseUrl(GATEWAY).requestInterceptor(interceptor);
        }

        @Bean
        MockRestServiceServer gatewayServer(RestClient.Builder gatewayRestClientBuilder) {
            return MockRestServiceServer.bindTo(gatewayRestClientBuilder).build();
        }

        @Bean
        @Primary
        RestClient mockGatewayRestClient(RestClient.Builder gatewayRestClientBuilder,
                                         MockRestServiceServer gatewayServer) {
            // gatewayServer parameter forces the mock to bind to the builder before it is built.
            return gatewayRestClientBuilder.build();
        }
    }
}
