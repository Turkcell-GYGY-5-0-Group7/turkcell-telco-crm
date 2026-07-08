package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.platform.starter.security.JwtService;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.platform.mediator.Mediator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP-level integration tests for the subscription lifecycle and read endpoints (feature 9.3, ADR-013).
 *
 * <p>Boots the full Spring context (real Mediator pipeline + TransactionBehavior, Spring Security,
 * Flyway schema + MSISDN seed, real JDBC outbox) against a Testcontainers Postgres, and drives the
 * REST surface with JWTs issued by {@link JwtService}. Asserts state machine transitions, illegal
 * transitions (-> 4xx), ownership enforcement (-> 403), reads, and outbox emissions.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class SubscriptionLifecycleIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    // Matches telco.platform.security.jwt.secret in src/test/resources/application-test.yml.
    // JwtService.issue() has no customerId-claim overload (it is a test/identity-service-only
    // helper on starter-security, out of this agent's authority to extend), so tokens carrying a
    // resolved customerId claim (identity-to-customer linkage, ADR-011) are minted locally here with
    // the same HMAC key, mirroring JwtAuthFilter#fromBearerToken's expected "customerId" claim shape.
    private static final String JWT_SECRET = "bXktdGVzdC1zZWNyZXQta2V5LWZvci1zdWJzY3JpcHRpb24tc3Zj";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    Mediator mediator;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    private RestClient client;
    private UUID customerId;
    private String customerToken;
    private String otherCustomerToken;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        // Subject is a UUID matching Keycloak's sub claim. The same UUID is also carried as the
        // resolved customerId claim (identity-to-customer linkage, ADR-011), so both the raw-subject
        // ownership checks (single get/suspend/reactivate/terminate) and the resolved-customerId
        // ownership check (list-by-customer) pass for this customer's own subscriptions.
        customerId = UUID.randomUUID();
        customerToken = issueWithCustomerId(customerId.toString(), Set.of("SUBSCRIBER"), customerId.toString());
        otherCustomerToken = issueWithCustomerId(
                UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), UUID.randomUUID().toString());
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

    /** Activates a subscription directly via the mediator (bypassing the internal HTTP endpoint). */
    private UUID activate(UUID ownerId) {
        return mediator.send(new ActivateSubscriptionCommand(
                UUID.randomUUID(), ownerId, "TARIFF_BASIC", 1));
    }

    private long outboxCount(String eventType, UUID aggregateId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Long.class, eventType, aggregateId.toString());
    }

    private String status(UUID subscriptionId) {
        return jdbc.queryForObject(
                "SELECT status FROM subscriptions WHERE id = ?", String.class, subscriptionId);
    }

    // --- 9.3.1 internal activation endpoint ---

    @Test
    void post_internal_subscriptions_activates_and_emits_activated_event() {
        UUID cid = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/internal/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "orderId", UUID.randomUUID().toString(),
                        "customerId", cid.toString(),
                        "tariffCode", "TARIFF_BASIC",
                        "tariffVersion", 1))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID subscriptionId = UUID.fromString((String) data(response));
        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
        assertThat(outboxCount("subscription.activated.v1", subscriptionId)).isEqualTo(1L);
        assertThat(outboxCount("msisdn.allocated.v1", subscriptionId)).isEqualTo(1L);
    }

    // --- 9.3.2 lifecycle endpoints (happy paths) ---

    @Test
    void suspend_then_reactivate_then_terminate_transitions_and_emits_events() {
        UUID subscriptionId = activate(customerId);

        // suspend
        assertThat(post("/api/v1/subscriptions/" + subscriptionId + "/suspend",
                Map.of("reason", "ADMIN_HOLD")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(subscriptionId)).isEqualTo("SUSPENDED");
        assertThat(outboxCount("subscription.suspended.v1", subscriptionId)).isEqualTo(1L);

        // reactivate -> re-emits subscription.activated.v1
        assertThat(post("/api/v1/subscriptions/" + subscriptionId + "/reactivate", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
        // one from activation + one from reactivation
        assertThat(outboxCount("subscription.activated.v1", subscriptionId)).isEqualTo(2L);

        // terminate -> releases MSISDN + emits subscription.terminated.v1
        String msisdn = jdbc.queryForObject(
                "SELECT msisdn FROM subscriptions WHERE id = ?", String.class, subscriptionId);
        assertThat(post("/api/v1/subscriptions/" + subscriptionId + "/terminate", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(subscriptionId)).isEqualTo("TERMINATED");
        assertThat(outboxCount("subscription.terminated.v1", subscriptionId)).isEqualTo(1L);
        assertThat(outboxCount("msisdn.released.v1", subscriptionId)).isEqualTo(1L);
        String poolStatus = jdbc.queryForObject(
                "SELECT status FROM msisdn_pool WHERE msisdn = ?", String.class, msisdn);
        assertThat(poolStatus).isEqualTo("FREE");
    }

    // --- 9.3.2 illegal transitions -> 4xx ---

    @Test
    void reactivate_active_subscription_is_rejected_4xx() {
        UUID subscriptionId = activate(customerId);
        ResponseEntity<Map<String, Object>> response =
                post("/api/v1/subscriptions/" + subscriptionId + "/reactivate", null);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
    }

    @Test
    void terminate_twice_is_rejected_4xx() {
        UUID subscriptionId = activate(customerId);
        assertThat(post("/api/v1/subscriptions/" + subscriptionId + "/terminate", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map<String, Object>> second =
                post("/api/v1/subscriptions/" + subscriptionId + "/terminate", null);
        assertThat(second.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void lifecycle_endpoint_requires_authentication_401() {
        UUID subscriptionId = activate(customerId);
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/subscriptions/" + subscriptionId + "/suspend")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- 9.3.2 ownership enforcement (IDOR guard) ---

    @Test
    void suspend_another_customers_subscription_is_rejected_403() {
        UUID subscriptionId = activate(customerId);
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/subscriptions/" + subscriptionId + "/suspend")
                .header("Authorization", "Bearer " + otherCustomerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "HACK"))
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(subscriptionId)).isEqualTo("ACTIVE");
    }

    // --- 9.3.3 reads ---

    @Test
    void get_subscription_returns_status_msisdn_tariff() {
        UUID subscriptionId = activate(customerId);
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions/" + subscriptionId)
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) data(response);
        assertThat(data.get("status")).isEqualTo("ACTIVE");
        assertThat(data.get("msisdn")).isNotNull();
        assertThat(data.get("tariffCode")).isEqualTo("TARIFF_BASIC");
    }

    @Test
    void get_another_customers_subscription_is_rejected_403() {
        UUID subscriptionId = activate(customerId);
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions/" + subscriptionId)
                .header("Authorization", "Bearer " + otherCustomerToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void get_missing_subscription_returns_404() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customer_with_multiple_subscriptions_returns_all() {
        activate(customerId);
        activate(customerId);
        activate(customerId);

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions?customerId=" + customerId + "&page=0&size=20")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) data(response);
        assertThat(((Number) page.get("totalElements")).intValue()).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) page.get("content");
        assertThat(content).hasSize(3);
    }

    @Test
    void list_another_customers_subscriptions_is_rejected_403() {
        activate(customerId);
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions?customerId=" + customerId + "&page=0&size=20")
                .header("Authorization", "Bearer " + otherCustomerToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Identity-to-customer linkage (ADR-011) null-safety requirement: a caller with no resolved
     * customerId claim (unlinked subscriber) must be denied, even when the requested customerId in
     * the query param happens to equal their own raw JWT subject - the exact value the old,
     * pre-fix comparison against {@code authentication.getName()} would have wrongly accepted.
     */
    @Test
    void unlinked_subscriber_listing_by_own_subject_as_customer_id_is_rejected_403() {
        String unlinkedToken = jwtService.issue(customerId.toString(), Set.of("SUBSCRIBER"));

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/subscriptions?customerId=" + customerId + "&page=0&size=20")
                .header("Authorization", "Bearer " + unlinkedToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map<String, Object>> post(String uri, Map<String, Object> body) {
        var spec = client.post()
                .uri(uri)
                .header("Authorization", "Bearer " + customerToken);
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.retrieve().toEntity(MAP_TYPE);
    }

    private static Object data(ResponseEntity<Map<String, Object>> response) {
        return response.getBody().get("data");
    }
}
