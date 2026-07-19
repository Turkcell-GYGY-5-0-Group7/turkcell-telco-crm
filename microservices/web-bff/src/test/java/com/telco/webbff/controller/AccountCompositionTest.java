package com.telco.webbff.controller;

import com.telco.platform.starter.security.JwtService;
import com.telco.webbff.client.GatewayClient;
import com.telco.webbff.config.BearerTokenRelayInterceptor;
import com.telco.webbff.service.AccountCompositionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves 16.5.1 acceptance criteria offline: the home, account and invoices endpoints compose real
 * gateway routes without a live stack. The gateway {@link RestClient} is replaced with one bound to a
 * {@link MockRestServiceServer}, so the full chain (controller -> {@link AccountCompositionService}
 * -> {@link GatewayClient} -> {@link BearerTokenRelayInterceptor}) runs against canned gateway
 * responses.
 *
 * <p>Boots in Bearer mode (matching {@link OnboardingCompositionTest}) so the platform
 * {@link JwtService} validates the caller's token. Because these reads are scoped to the caller's
 * resolved {@code customerId} (the identity-to-customer linkage claim, ADR-011), the tokens here carry
 * a {@code customerId} claim - minted with {@link #issueWithCustomerId} because {@link JwtService#issue}
 * intentionally has no customerId overload (starter-security is out of this agent's authority).
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
                "telco.platform.security.jwt.issuer=telco"
        }
)
@ActiveProfiles("test")
class AccountCompositionTest {

    private static final String GATEWAY = "http://gateway.mock";
    private static final String JWT_SECRET = "d2ViLWJmZi10ZXN0LXNlY3JldC1rZXktZm9yLXRlc3RpbmctMjAyNg==";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockRestServiceServer gatewayServer;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String customerId;
    private String token;

    @BeforeEach
    void setUp() {
        gatewayServer.reset();
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();
        // Subject (Keycloak sub) and resolved customerId are deliberately different UUIDs, so a test
        // that asserts the caller's customerId flows downstream cannot pass by accident on the subject.
        customerId = UUID.randomUUID().toString();
        token = issueWithCustomerId(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), customerId);
    }

    // --- home ---------------------------------------------------------------------------------

    @Test
    void home_composes_profile_active_subscriptions_and_latest_invoice_in_one_call() {
        String activeSub = UUID.randomUUID().toString();
        String invoiceId = UUID.randomUUID().toString();

        gatewayServer.expect(requestTo(containsString("/api/v1/customers/" + customerId)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(customer("Ada", "Lovelace", "ACTIVE"), MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/subscriptions?customerId=" + customerId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(subscriptionPage(
                        subscription(activeSub, "+905550000001", "TRF-1", "ACTIVE"),
                        subscription(UUID.randomUUID().toString(), "+905550000002", "TRF-9", "TERMINATED")),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/invoices?customerId=" + customerId + "&page=0&size=1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(invoicePage(0, 1, 1, 1,
                        invoice(invoiceId, "199.90", "PAID")), MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = get("/bff/v1/home");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"profile\"").contains("Ada Lovelace").contains(customerId)
                .contains("\"activeSubscriptions\"").contains("+905550000001").contains("TRF-1")
                // the TERMINATED subscription is filtered out of the dashboard's active list
                .doesNotContain("+905550000002")
                .contains("\"latestInvoice\"").contains("2026-06").contains("199.90")
                .contains("/api/v1/invoices/" + invoiceId + "/pdf");
        gatewayServer.verify();
    }

    // --- account ------------------------------------------------------------------------------

    @Test
    void account_includes_usage_quota_per_active_subscription() {
        String activeSub = UUID.randomUUID().toString();

        gatewayServer.expect(requestTo(containsString("/api/v1/customers/" + customerId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(customer("Ada", "Lovelace", "ACTIVE"), MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/subscriptions?customerId=" + customerId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(subscriptionPage(
                        subscription(activeSub, "+905550000001", "TRF-1", "ACTIVE")),
                        MediaType.APPLICATION_JSON));
        gatewayServer.expect(requestTo(containsString("/api/v1/usage/subscriptions/" + activeSub + "/quota")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(quota(activeSub), MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = get("/bff/v1/account");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"subscriptions\"").contains("+905550000001")
                .contains("\"usage\"")
                // used = total - remaining: data 10000-6500=3500, voice 1000-880=120, sms 500-455=45
                .contains("\"dataUsedMb\":3500").contains("\"dataAllowanceMb\":10000")
                .contains("\"voiceUsedMinutes\":120").contains("\"voiceAllowanceMinutes\":1000")
                .contains("\"smsUsed\":45").contains("\"smsAllowance\":500");
        gatewayServer.verify();
    }

    // --- invoices -----------------------------------------------------------------------------

    @Test
    void invoices_returns_a_paged_list_each_with_a_pdf_link() {
        String invoiceId = UUID.randomUUID().toString();

        gatewayServer.expect(requestTo(containsString("/api/v1/invoices?customerId=" + customerId + "&page=1&size=5")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(invoicePage(1, 5, 7, 2,
                        invoice(invoiceId, "42.00", "OVERDUE")), MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = get("/bff/v1/invoices?page=1&size=5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"invoices\"").contains("2026-06").contains("42.00").contains("OVERDUE")
                .contains("/api/v1/invoices/" + invoiceId + "/pdf")
                // paging metadata mirrored from billing so the UI can page
                .contains("\"page\":1").contains("\"size\":5")
                .contains("\"totalElements\":7").contains("\"totalPages\":2");
        gatewayServer.verify();
    }

    // --- self-scoping (security) --------------------------------------------------------------

    @Test
    void reads_are_scoped_to_caller_ignoring_a_client_supplied_customerId() {
        String attacker = UUID.randomUUID().toString();
        String invoiceId = UUID.randomUUID().toString();

        // The downstream call MUST carry the caller's own resolved customerId, never the attacker id
        // smuggled in via the query string. The BFF endpoint binds no customerId param, so the value
        // is ignored; here we assert the outbound gateway URI proves it.
        gatewayServer.expect(requestTo(allOf(
                        containsString("customerId=" + customerId),
                        not(containsString(attacker)))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(invoicePage(0, 20, 1, 1,
                        invoice(invoiceId, "10.00", "PAID")), MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = get("/bff/v1/invoices?customerId=" + attacker);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(attacker);
        gatewayServer.verify();
    }

    @Test
    void home_is_forbidden_when_the_identity_has_no_linked_customer() {
        // A SUBSCRIBER token with no customerId linkage claim cannot own account data; the read is
        // refused (403) before any gateway call rather than widening scope. No gateway expectations.
        String unlinked = jwtService.issue(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"));

        ResponseEntity<String> response = client.get().uri("/bff/v1/home")
                .header("Authorization", "Bearer " + unlinked)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        gatewayServer.verify();
    }

    // --- helpers ------------------------------------------------------------------------------

    private ResponseEntity<String> get(String uri) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private static String apiOk(String dataJson) {
        return "{\"success\":true,\"data\":" + dataJson + "}";
    }

    private static String customer(String firstName, String lastName, String status) {
        return apiOk("{\"id\":\"" + UUID.randomUUID() + "\",\"firstName\":\"" + firstName
                + "\",\"lastName\":\"" + lastName + "\",\"status\":\"" + status + "\"}");
    }

    private static String subscription(String id, String msisdn, String tariffCode, String status) {
        return "{\"id\":\"" + id + "\",\"customerId\":\"" + UUID.randomUUID() + "\",\"msisdn\":\"" + msisdn
                + "\",\"tariffCode\":\"" + tariffCode + "\",\"status\":\"" + status + "\"}";
    }

    private static String subscriptionPage(String... subscriptions) {
        return apiOk("{\"content\":[" + String.join(",", subscriptions)
                + "],\"page\":0,\"size\":100,\"totalElements\":" + subscriptions.length + ",\"totalPages\":1}");
    }

    private static String invoice(String id, String grandTotal, String status) {
        return "{\"id\":\"" + id + "\",\"customerId\":\"" + UUID.randomUUID()
                + "\",\"subscriptionId\":\"" + UUID.randomUUID()
                + "\",\"periodStart\":\"2026-06-01T00:00:00Z\",\"periodEnd\":\"2026-06-30T23:59:59Z\""
                + ",\"grandTotal\":" + grandTotal + ",\"currency\":\"TRY\",\"status\":\"" + status + "\"}";
    }

    private static String invoicePage(int page, int size, long total, int totalPages, String... invoices) {
        return apiOk("{\"content\":[" + String.join(",", invoices) + "],\"page\":" + page + ",\"size\":" + size
                + ",\"totalElements\":" + total + ",\"totalPages\":" + totalPages + "}");
    }

    private static String quota(String subscriptionId) {
        return apiOk("{\"subscriptionId\":\"" + subscriptionId
                + "\",\"minutesTotal\":1000,\"smsTotal\":500,\"mbTotal\":10000"
                + ",\"minutesRemaining\":880,\"smsRemaining\":455,\"mbRemaining\":6500}");
    }

    /**
     * Mints an HMAC-signed token carrying a {@code customerId} claim, the shape
     * {@code JwtAuthFilter#fromBearerToken} resolves into {@code CurrentUserProvider}. Local to this
     * test because {@link JwtService#issue} intentionally has no customerId-claim overload
     * (starter-security is out of this agent's authority to extend).
     */
    private static String issueWithCustomerId(String subject, Set<String> roles, String customerId) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("telco")
                .subject(subject)
                .claim("roles", List.copyOf(roles))
                .claim("customerId", customerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
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
