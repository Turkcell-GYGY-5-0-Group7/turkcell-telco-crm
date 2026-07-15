package com.telco.campaign;

import com.telco.campaign.application.command.ConfirmRedemptionCommand;
import com.telco.campaign.application.command.CreateRedemptionReservationCommand;
import com.telco.campaign.domain.model.CampaignRedemption;
import com.telco.campaign.domain.model.RedemptionStatus;
import com.telco.campaign.domain.service.CampaignEligibilityService;
import com.telco.campaign.infrastructure.persistence.CampaignRedemptionRepository;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.outbox.OutboxService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for campaign-service (Feature 21.5.2, ADR-013), mirroring
 * {@code ProductCatalogServiceIntegrationTest}'s style: Testcontainers Postgres, real Mediator/
 * Spring Security/Flyway pipeline, only {@link OutboxService} mocked. HMAC-signed JWTs from
 * {@link JwtService#issue} stand in for Keycloak.
 *
 * <p>Drives the full create -&gt; activate -&gt; validate -&gt; (simulate {@code order.created.v1})
 * -&gt; reserve -&gt; (simulate {@code payment.completed.v1}) -&gt; confirm -&gt;
 * cap-exceeded-on-next-attempt flow end to end against a real database (21.5.2 acceptance
 * criteria), and proves idempotent redelivery of {@link ConfirmRedemptionCommand} through the real
 * platform {@code InboxBehavior}/inbox table (duplicate message -&gt; single effect), the strongest
 * form of that proof in this feature (the mocked-mediator consumer tests only prove idempotency-key
 * stability, not the real dedup mechanism).
 *
 * <p>The Kafka listener containers never start in tests ({@code spring.kafka.listener.auto-startup:
 * false}, {@code application-test.yml}) - "simulating" {@code order.created.v1}/
 * {@code payment.completed.v1} consumption means dispatching the exact same {@link Mediator}
 * command the real {@code @KafkaListener} consumer would dispatch for that event, which is the same
 * simulation technique every consumer unit test in this module already uses.
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
class CampaignServiceIntegrationTest {

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

    @MockitoBean
    OutboxService outboxService;

    @Autowired
    JwtService jwtService;

    @Autowired
    Mediator mediator;

    @Autowired
    CampaignEligibilityService campaignEligibilityService;

    @Autowired
    CampaignRedemptionRepository campaignRedemptionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE campaign_redemptions, campaign_tariff_codes, campaigns CASCADE");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue("admin-user-id", Set.of("ADMIN"));
    }

    @Test
    void full_lifecycle_reserve_confirm_and_per_customer_cap_enforced_end_to_end() {
        UUID customerId = UUID.randomUUID();
        String tariffCode = "POSTPAID-E2E";

        // 1. create (DRAFT)
        ResponseEntity<Map<String, Object>> createResponse = client.post()
                .uri("/api/v1/campaigns")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createCampaignJson("E2E25", tariffCode))
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> created = data(createResponse);
        assertThat(created.get("status")).isEqualTo("DRAFT");
        UUID campaignId = UUID.fromString((String) created.get("id"));

        // 2. activate (DRAFT -> ACTIVE)
        ResponseEntity<Map<String, Object>> activateResponse = client.post()
                .uri("/api/v1/campaigns/" + campaignId + "/activate")
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(activateResponse).get("status")).isEqualTo("ACTIVE");

        // 3. validate -> eligible, real Postgres-backed eligibility decision
        ResponseEntity<Map<String, Object>> firstValidate = validate(customerId, tariffCode);
        assertThat(firstValidate.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> firstDecision = data(firstValidate);
        assertThat(firstDecision.get("eligible")).isEqualTo(true);
        assertThat(firstDecision.get("campaignId")).isEqualTo(campaignId.toString());
        assertThat(firstDecision.get("discountType")).isEqualTo("PERCENTAGE");

        // 4. simulate order.created.v1 consumption -> RESERVED redemption
        UUID orderId = UUID.randomUUID();
        mediator.send(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "order-msg-1:" + campaignId));

        List<CampaignRedemption> afterReserve =
                campaignRedemptionRepository.findByCampaignIdAndCustomerId(campaignId, customerId);
        assertThat(afterReserve).hasSize(1);
        assertThat(afterReserve.get(0).getStatus()).isEqualTo(RedemptionStatus.RESERVED);
        assertThat(afterReserve.get(0).getOrderId()).isEqualTo(orderId);

        // 5. simulate payment.completed.v1 consumption -> RESERVED -> CONFIRMED
        mediator.send(new ConfirmRedemptionCommand(orderId, "payment-msg-1"));

        CampaignRedemption confirmed = campaignRedemptionRepository.findByOrderId(orderId).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(RedemptionStatus.CONFIRMED);

        // 5b. idempotent redelivery (21.5.2 acceptance criteria): redelivering the exact same
        // payment.completed.v1 messageId must be a no-op through the real InboxBehavior/inbox table
        // (duplicate message -> single effect), not just a stable command shape at the mocked-mediator
        // unit-test level.
        mediator.send(new ConfirmRedemptionCommand(orderId, "payment-msg-1"));
        List<CampaignRedemption> afterRedelivery =
                campaignRedemptionRepository.findByCampaignIdAndCustomerId(campaignId, customerId);
        assertThat(afterRedelivery).hasSize(1);
        assertThat(afterRedelivery.get(0).getStatus()).isEqualTo(RedemptionStatus.CONFIRMED);
        assertThat(afterRedelivery.get(0).getConfirmedAt()).isEqualTo(confirmed.getConfirmedAt());

        // 6. subsequent attempt at perCustomerRedemptionCap (=1, already consumed) is correctly
        // rejected - both at the domain-service write path (BusinessRuleException) and reflected back
        // through the synchronous validate read.
        UUID secondOrderId = UUID.randomUUID();
        assertThatThrownBy(() -> campaignEligibilityService.reserve(campaignId, customerId, secondOrderId))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(campaignRedemptionRepository.findByCampaignIdAndCustomerId(campaignId, customerId))
                .hasSize(1);

        ResponseEntity<Map<String, Object>> secondValidate = validate(customerId, tariffCode);
        assertThat(secondValidate.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> secondDecision = data(secondValidate);
        assertThat(secondDecision.get("eligible")).isEqualTo(false);
        assertThat(secondDecision.get("reason")).isEqualTo("PER_CUSTOMER_CAP_EXCEEDED");

        // 6b. the same cap-exceeded outcome reached through the real order.created.v1 consumption
        // path (CreateRedemptionReservationCommandHandler) is swallowed as a WARN, not propagated -
        // no second redemption row is ever created for this customer/campaign.
        mediator.send(new CreateRedemptionReservationCommand(
                campaignId, customerId, secondOrderId, "order-msg-2:" + campaignId));
        assertThat(campaignRedemptionRepository.findByCampaignIdAndCustomerId(campaignId, customerId))
                .hasSize(1);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> validate(UUID customerId, String tariffCode) {
        return client.post()
                .uri("/internal/campaigns/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"customerId":"%s","tariffCode":"%s"}
                        """.formatted(customerId, tariffCode))
                .retrieve()
                .toEntity(MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private static String createCampaignJson(String code, String tariffCode) {
        return """
                {
                  "code": "%s",
                  "name": "End to end campaign",
                  "description": "Feature 21.5.2 integration test fixture",
                  "discountType": "PERCENTAGE",
                  "discountValue": 25.00,
                  "applicableTariffCodes": ["%s"],
                  "validFrom": "2020-01-01T00:00:00Z",
                  "validTo": "2099-01-01T00:00:00Z",
                  "totalRedemptionCap": null,
                  "perCustomerRedemptionCap": 1
                }
                """.formatted(code, tariffCode);
    }
}
