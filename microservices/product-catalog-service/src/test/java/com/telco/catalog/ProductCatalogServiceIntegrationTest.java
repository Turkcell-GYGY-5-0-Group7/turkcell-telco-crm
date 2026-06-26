package com.telco.catalog;

import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for product-catalog-service (feature 7.5.1, ADR-013).
 *
 * <p>Uses Testcontainers Postgres and Redis. HMAC-signed JWTs from {@link JwtService#issue} so
 * no Keycloak instance is needed. {@link OutboxService} is mocked — the rest (Mediator pipeline,
 * TransactionBehavior, Spring Security, Cache) runs real.
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
class ProductCatalogServiceIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @MockitoBean
    OutboxService outboxService;

    @Autowired
    JwtService jwtService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue("admin-user-id", Set.of("ADMIN"));
        customerToken = jwtService.issue("customer-user-id", Set.of("CUSTOMER"));

        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void unauthenticated_request_on_protected_endpoint_returns_401() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/tariffs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void non_admin_cannot_create_tariff_returns_403() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/tariffs")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validCreateTariffJson("NONAUTH001"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_creates_tariff_returns_201_with_correct_body() {
        ResponseEntity<Map<String, Object>> response = createTariff("POSTPAID-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("code")).isEqualTo("POSTPAID-001");
        assertThat(data.get("status")).isEqualTo("DRAFT");
        assertThat(data.get("id")).isNotNull();
    }

    @Test
    void duplicate_tariff_code_returns_422() {
        createTariff("DUPE-001");

        ResponseEntity<Map<String, Object>> second = client.post()
                .uri("/api/v1/tariffs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validCreateTariffJson("DUPE-001"))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
    }

    @Test
    void admin_changes_tariff_price_creates_new_version() {
        createTariff("PRICE-001");

        ResponseEntity<Map<String, Object>> priceResponse = client.patch()
                .uri("/api/v1/tariffs/PRICE-001/price")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"monthlyFee": 99.99}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(priceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) priceResponse.getBody().get("data");
        assertThat(data.get("version")).isEqualTo(2);
        assertThat(data.get("monthlyFee")).isEqualTo(99.99);
    }

    @Test
    void non_admin_cannot_change_price_returns_403() {
        createTariff("PRICE-PROTECT");

        ResponseEntity<String> response = client.patch()
                .uri("/api/v1/tariffs/PRICE-PROTECT/price")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"monthlyFee": 50.00}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknown_tariff_price_snapshot_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/tariffs/NONEXISTENT/price-snapshot")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_tariffs_returns_200() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/tariffs")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
    }

    @Test
    void list_addons_without_filter_returns_200() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/addons")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
    }

    @Test
    void list_addons_with_tariff_code_returns_200() {
        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/addons?tariffCode=NONEXISTENT")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void price_snapshot_endpoint_requires_no_auth_and_draft_returns_404() {
        createTariff("SNAP-001");

        ResponseEntity<String> snapResponse = client.get()
                .uri("/api/v1/tariffs/SNAP-001/price-snapshot")
                .retrieve()
                .toEntity(String.class);

        assertThat(snapResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void draft_tariff_not_returned_by_get_single_returns_404() {
        createTariff("DRAFT-404");

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/tariffs/DRAFT-404")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> createTariff(String code) {
        return client.post()
                .uri("/api/v1/tariffs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validCreateTariffJson(code))
                .retrieve()
                .toEntity(MAP_TYPE);
    }

    private static String validCreateTariffJson(String code) {
        return """
                {
                  "code": "%s",
                  "name": "Test Tariff %s",
                  "type": "POSTPAID",
                  "monthlyFee": 49.99,
                  "currency": "TRY",
                  "minutesIncluded": 500,
                  "smsIncluded": 100,
                  "dataMbIncluded": 10240,
                  "effectiveFrom": "2026-01-01T00:00:00Z"
                }
                """.formatted(code, code);
    }
}
