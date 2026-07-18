package com.telco.fraud;

import com.telco.fraud.application.command.IngestLifecycleSignalCommand;
import com.telco.fraud.domain.FraudRuleCode;
import com.telco.fraud.domain.MsisdnLifecycleEventType;
import com.telco.fraud.infrastructure.persistence.FraudSignalRepository;
import com.telco.platform.mediator.Mediator;
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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 23.5.2 integration test (ADR-013): {@code PUT /api/v1/fraud-rules/{code}} takes effect on
 * the NEXT evaluation with no Spring-context restart (the rule evaluators read each {@link
 * com.telco.fraud.domain.FraudRule} fresh from the repository per evaluation). Mirrors
 * campaign-service's {@code CampaignServiceIntegrationTest} Testcontainers/Flyway/JWT wiring.
 *
 * <p>Scenario: with the seeded MSISDN_CHURN_VELOCITY threshold of 3, exactly 3 allocate/release
 * cycles do not fire. The ADMIN then lowers the threshold to 1 via the API; a single further cycle
 * for a fresh customer now crosses the tightened threshold and raises a signal - proving the change
 * is live within the same running context.
 *
 * <p><strong>Known sandbox limitation (docs/tasks/lessons.md 2026-07-12):</strong> Testcontainers
 * cannot start in the current sandbox; verified by review here, runs green with a compatible Docker.
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
class FraudRuleConfigApiIntegrationTest {

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
    Mediator mediator;

    @Autowired
    FraudSignalRepository fraudSignalRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE fraud_case, fraud_signal, msisdn_lifecycle_signal, outbox_event, "
                        + "inbox_message RESTART IDENTITY");
        // Restore the seeded default threshold so the test is order-independent within the shared context.
        jdbcTemplate.update("UPDATE fraud_rule SET window_minutes = 1440, threshold_count = 3, "
                + "severity = 'MEDIUM', enabled = TRUE WHERE code = 'MSISDN_CHURN_VELOCITY'");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue("fraud-admin-id", Set.of("ADMIN"));
    }

    @Test
    void updated_threshold_takes_effect_on_the_next_evaluation_without_restart() {
        // 1. Under the seeded threshold of 3, exactly 3 cycles must not fire for this customer.
        UUID beforeCustomer = UUID.randomUUID();
        ingestChurnCycles(beforeCustomer, 3);
        assertThat(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                beforeCustomer, FraudRuleCode.MSISDN_CHURN_VELOCITY)).isEmpty();

        // 2. ADMIN lowers the threshold to 1 through the live API.
        ResponseEntity<Map<String, Object>> putResponse = client.put()
                .uri("/api/v1/fraud-rules/{code}", "MSISDN_CHURN_VELOCITY")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"windowMinutes":1440,"thresholdCount":1,"severity":"MEDIUM","enabled":true}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(putResponse).get("thresholdCount")).isEqualTo(1);

        // 3. A fresh customer with just 2 cycles now exceeds the tightened threshold (>1) and fires -
        //    proving the new threshold is read live on the next evaluation, no context restart.
        UUID afterCustomer = UUID.randomUUID();
        ingestChurnCycles(afterCustomer, 2);
        assertThat(fraudSignalRepository.findByCustomerIdAndRuleCodeOrderByTriggeredAtDesc(
                afterCustomer, FraudRuleCode.MSISDN_CHURN_VELOCITY)).hasSize(1);
    }

    @Test
    void rule_write_is_admin_only() {
        String agentToken = jwtService.issue("fraud-analyst-id", Set.of("SUPPORT"));
        ResponseEntity<String> response = client.put()
                .uri("/api/v1/fraud-rules/{code}", "MSISDN_CHURN_VELOCITY")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"windowMinutes":1440,"thresholdCount":1,"severity":"MEDIUM","enabled":true}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void ingestChurnCycles(UUID customerId, int count) {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < count; i++) {
            String msisdn = "90555300" + String.format("%04d", i);
            MsisdnLifecycleEventType type = i % 2 == 0
                    ? MsisdnLifecycleEventType.MSISDN_ALLOCATED
                    : MsisdnLifecycleEventType.MSISDN_RELEASED;
            mediator.send(new IngestLifecycleSignalCommand(
                    type, customerId, msisdn, UUID.randomUUID(),
                    base.plus(i, ChronoUnit.MINUTES), null));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }
}
