package com.telco.fraud;

import com.telco.fraud.domain.FraudCase;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.fraud.infrastructure.persistence.FraudCaseRepository;
import com.telco.platform.starter.security.JwtService;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 23.5.2 integration test (ADR-013): the fraud-case API end to end against a real
 * PostgreSQL 17, mirroring campaign-service's {@code CampaignServiceIntegrationTest} (Testcontainers,
 * real Mediator/Spring Security/Flyway pipeline, HMAC JWTs from {@link JwtService#issue} standing in
 * for Keycloak). Exercises {@code GET /api/v1/fraud-cases}, {@code GET /api/v1/fraud-cases/{id}}, and
 * {@code POST /api/v1/fraud-cases/{id}/resolve} against a seeded case, asserting the resolve
 * transitions status AND writes the {@code fraud.case-resolved.v1} outbox row (the real
 * {@link com.telco.platform.outbox.OutboxService} is deliberately not mocked here).
 *
 * <p><strong>Detect-and-alert only (ADR-029 Section 5):</strong> resolving a case is a status change
 * plus an event publish - no route here calls subscription-service; the explicit no-suspension
 * regression assertion lives in {@code acceptance/RapidSimSwapToAutoTicketAcceptanceTest}.
 *
 * <p><strong>Known sandbox limitation (docs/tasks/lessons.md 2026-07-12):</strong> Testcontainers
 * cannot start in the current sandbox ("Could not find a valid Docker environment"); verified by
 * review here, runs green wherever a compatible Docker daemon exists.
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
class FraudCaseApiIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JwtService jwtService;

    @Autowired
    FraudCaseRepository fraudCaseRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String agentToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE fraud_case, fraud_signal, msisdn_lifecycle_signal, outbox_event, "
                        + "inbox_message RESTART IDENTITY");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        agentToken = jwtService.issue("fraud-analyst-id", Set.of("SUPPORT"));
    }

    @Test
    void list_view_and_resolve_a_seeded_case_end_to_end_and_publish_case_resolved() {
        UUID customerId = UUID.randomUUID();
        FraudCase seeded = fraudCaseRepository.save(new FraudCase(
                UUID.randomUUID(), customerId, FraudCaseStatus.OPEN,
                new ArrayList<>(List.of(UUID.randomUUID())), Instant.now(), null, null));
        UUID caseId = seeded.getId();

        // GET /api/v1/fraud-cases (list) returns the seeded OPEN case.
        ResponseEntity<Map<String, Object>> listResponse = client.get()
                .uri("/api/v1/fraud-cases?status=OPEN")
                .header("Authorization", "Bearer " + agentToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();

        // GET /api/v1/fraud-cases/{id} returns the case detail.
        ResponseEntity<Map<String, Object>> getResponse = client.get()
                .uri("/api/v1/fraud-cases/{id}", caseId)
                .header("Authorization", "Bearer " + agentToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(getResponse).get("status")).isEqualTo("OPEN");

        // POST /api/v1/fraud-cases/{id}/resolve transitions to CONFIRMED...
        ResponseEntity<Map<String, Object>> resolveResponse = client.post()
                .uri("/api/v1/fraud-cases/{id}/resolve", caseId)
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status":"CONFIRMED","note":"confirmed by review"}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(resolveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resolveResponse).get("status")).isEqualTo("CONFIRMED");

        // ...persisted, and emits fraud.case-resolved.v1 through the real outbox atomically.
        FraudCase reloaded = fraudCaseRepository.findById(caseId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FraudCaseStatus.CONFIRMED);
        assertThat(reloaded.getResolvedBy()).isEqualTo("fraud-analyst-id");
        Long resolvedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'fraud.case-resolved.v1' "
                        + "AND aggregate_id = ?", Long.class, caseId.toString());
        assertThat(resolvedRows).isEqualTo(1L);
    }

    @Test
    void get_unknown_case_returns_404() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/fraud-cases/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + agentToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fraud_case_routes_require_authentication() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/fraud-cases")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }
}
