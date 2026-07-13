package com.telco.customer;

import com.telco.customer.infrastructure.storage.DocumentStorage;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the self-ownership authorization rule on {@link com.telco.customer.api.CustomerController}
 * (ADR-011; resolution of the interim staff-only gate recorded in
 * {@code docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md}).
 *
 * <p>The rule is
 * {@code hasAnyRole('ADMIN','CALL_CENTER_AGENT') or #id.toString() == @currentUserProvider.currentUser().customerId()}.
 * Since Sprint 14 feature 14.4, identity-service consumes {@code customer.registered.v1}, writes
 * {@code users.customer_id}, and mints a JWT carrying a {@code customerId} claim (the gateway
 * forwards the same value as {@code X-Customer-Id}). {@link JwtService#issue} does not set that
 * claim, so this test mints tokens directly with the configured HMAC secret to reproduce all four
 * caller shapes a live request can have: linked owner, linked non-owner, unlinked subscriber, staff.
 *
 * <p>The unlinked case is the null-safety trap the ruling calls out and that
 * {@code NotificationSecurityIntegrationTest} guards for notification-service: a null resolved
 * {@code customerId} must never equal-match, not even when the caller supplies their own raw JWT
 * subject as the path id.
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
class CustomerSelfOwnershipSecurityIntegrationTest {

    private static final String VALID_TCKN = "10000000146";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean OutboxService outboxService;
    @MockitoBean DocumentStorage documentStorage;

    @Autowired JdbcTemplate jdbc;

    @Value("${telco.platform.security.jwt.secret}") String jwtSecret;
    @Value("${telco.platform.security.jwt.issuer}") String jwtIssuer;

    @LocalServerPort int port;

    private RestClient client;
    private String registrarToken;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM addresses");
        jdbc.execute("DELETE FROM customers");

        registrarToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), null);

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        when(documentStorage.store(any(), any(), any())).thenReturn("kyc/doc.png");
    }

    // --- (a) the owner can read their own record ---

    @Test
    void linked_subscriber_can_read_own_customer_record() {
        String customerId = doRegister();
        String ownerToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), customerId);

        ResponseEntity<Map<String, Object>> response = get(customerId, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(customerId);
        // PII stays masked even for the owner (NFR-06, ADR-021).
        assertThat((String) data.get("identityNumberMasked")).matches("\\*+\\d{4}");
    }

    @Test
    void linked_subscriber_can_read_own_record_with_real_keycloak_token_shape() {
        // A real Keycloak-provisioned subscriber always also carries the realm default composite
        // role and its expanded client roles; those must not affect the ownership check.
        String customerId = doRegister();
        String ownerToken = token(UUID.randomUUID().toString(),
                Set.of("SUBSCRIBER", "default-roles-telco-crm", "offline_access", "uma_authorization"),
                customerId);

        assertThat(get(customerId, ownerToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void linked_subscriber_can_update_own_customer_record() {
        String customerId = doRegister();
        String ownerToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), customerId);

        ResponseEntity<Map<String, Object>> response = client.put()
                .uri("/api/v1/customers/{id}", customerId)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"firstName": "Grace", "lastName": "Hopper", "dateOfBirth": "1985-12-09"}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("firstName")).isEqualTo("Grace");
    }

    // --- (b) a different subscriber is denied ---

    @Test
    void different_linked_subscriber_cannot_read_someone_elses_customer_record() {
        String customerId = doRegister();
        String otherToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"),
                UUID.randomUUID().toString());

        assertThat(get(customerId, otherToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void different_linked_subscriber_cannot_update_someone_elses_customer_record() {
        String customerId = doRegister();
        String otherToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"),
                UUID.randomUUID().toString());

        ResponseEntity<String> response = client.put()
                .uri("/api/v1/customers/{id}", customerId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"firstName": "Mallory", "lastName": "Attacker", "dateOfBirth": "1985-12-09"}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- (c) an unlinked identity (null customerId) is denied - the null-equality trap ---

    @Test
    void unlinked_subscriber_is_denied_even_when_customer_id_claim_is_absent() {
        String customerId = doRegister();
        String unlinkedToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), null);

        assertThat(get(customerId, unlinkedToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unlinked_subscriber_passing_own_subject_as_the_path_id_is_denied() {
        // Guards against resolving ownership from the raw JWT subject instead of the linked claim:
        // the subject is used as the path id here, and must still not match a null customerId.
        String subject = UUID.randomUUID().toString();
        String unlinkedToken = token(subject, Set.of("SUBSCRIBER"), null);

        assertThat(get(subject, unlinkedToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void subscriber_with_blank_customer_id_claim_is_denied() {
        String customerId = doRegister();
        String blankLinkToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), "");

        assertThat(get(customerId, blankLinkToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- (d) staff access is retained ---

    @Test
    void admin_can_read_any_customer_record() {
        String customerId = doRegister();
        String adminToken = token(UUID.randomUUID().toString(), Set.of("ADMIN"), null);

        assertThat(get(customerId, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void call_center_agent_can_read_any_customer_record() {
        String customerId = doRegister();
        String agentToken = token(UUID.randomUUID().toString(), Set.of("CALL_CENTER_AGENT"), null);

        assertThat(get(customerId, agentToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- DELETE stays ADMIN-only, owners included ---

    @Test
    void owner_cannot_delete_own_customer_record() {
        String customerId = doRegister();
        String ownerToken = token(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), customerId);

        ResponseEntity<String> response = client.delete()
                .uri("/api/v1/customers/{id}", customerId)
                .header("Authorization", "Bearer " + ownerToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void call_center_agent_cannot_delete_customer_record() {
        String customerId = doRegister();
        String agentToken = token(UUID.randomUUID().toString(), Set.of("CALL_CENTER_AGENT"), null);

        ResponseEntity<String> response = client.delete()
                .uri("/api/v1/customers/{id}", customerId)
                .header("Authorization", "Bearer " + agentToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- Helpers ---

    private ResponseEntity<Map<String, Object>> get(String customerId, String bearer) {
        return client.get()
                .uri("/api/v1/customers/{id}", customerId)
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .toEntity(MAP_TYPE);
    }

    private String doRegister() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + registrarToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "type": "INDIVIDUAL",
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "identityNumber": "%s",
                            "dateOfBirth": "1990-01-01"
                        }
                        """.formatted(VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Map<?, ?>) response.getBody().get("data")).get("id").toString();
    }

    /**
     * Mints a token in the exact shape identity-service issues since feature 14.4: the same claims
     * {@link JwtService#issue} sets, plus the linked {@code customerId} claim when non-null. Signed
     * with the configured HMAC secret so {@code JwtAuthFilter} accepts it.
     */
    private String token(String subject, Set<String> roles, String customerId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(jwtIssuer)
                .subject(subject)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)));
        if (customerId != null) {
            builder.claim("customerId", customerId);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).compact();
    }
}
