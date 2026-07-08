package com.telco.catalog;

import com.telco.catalog.domain.model.Tariff;
import com.telco.catalog.domain.model.TariffType;
import com.telco.catalog.infrastructure.persistence.TariffRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @Autowired
    TariffRepository tariffRepository;

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
        customerToken = jwtService.issue("customer-user-id", Set.of("SUBSCRIBER"));

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
        assertThat(data.get("status")).isEqualTo("ACTIVE");
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
                .uri("/internal/tariffs/NONEXISTENT/price-snapshot")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void old_public_price_snapshot_path_now_requires_auth() {
        // The route moved to /internal/tariffs/{code}/price-snapshot (tech-lead ruling
        // 2026-07-06, tariff endpoint internal-surface fix). The old /api/v1 path is no longer
        // registered at all, so Spring MVC falls through to .anyRequest().authenticated() -> 401,
        // not a 200 with tariff data.
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/tariffs/NONEXISTENT/price-snapshot")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
    void get_tariff_twice_returns_200_on_cache_miss_and_cache_hit() {
        // Regression test for the Sprint 14 load-test bug: TariffResponse is cached in Redis via
        // a polymorphically-typed ObjectMapper, but TariffResponse is a Java record (implicitly
        // final). DefaultTyping.NON_FINAL never wrote "@class" type metadata for final classes,
        // so the value serialized on the first (cache-miss) call could not be deserialized on the
        // second (cache-hit) call and GET /api/v1/tariffs/{code} returned 500 every time after the
        // first request. This must exercise the real RedisCacheManager/serializer, not a mock.
        createTariff("CACHE-HIT-001");

        ResponseEntity<Map<String, Object>> firstCall = client.get()
                .uri("/api/v1/tariffs/CACHE-HIT-001")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(firstCall.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Confirm the value actually landed in Redis (i.e. the second call below is a genuine
        // cache HIT, not merely a second cache MISS re-reading Postgres).
        assertThat(redisTemplate.keys("tariffs*")).isNotEmpty();

        ResponseEntity<Map<String, Object>> secondCall = client.get()
                .uri("/api/v1/tariffs/CACHE-HIT-001")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(secondCall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondCall.getBody().get("data")).isEqualTo(firstCall.getBody().get("data"));
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
        persistDraftTariff("SNAP-001");

        ResponseEntity<String> snapResponse = client.get()
                .uri("/internal/tariffs/SNAP-001/price-snapshot")
                .retrieve()
                .toEntity(String.class);

        assertThat(snapResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void draft_tariff_not_returned_by_get_single_returns_404() {
        persistDraftTariff("DRAFT-404");

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/tariffs/DRAFT-404")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void draft_tariff_not_returned_by_get_by_id_returns_404() {
        Tariff draft = persistDraftTariff("DRAFT-BY-ID-404");

        ResponseEntity<String> response = client.get()
                .uri("/internal/tariffs/{id}", draft.getId())
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknown_tariff_id_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/internal/tariffs/{id}", UUID.randomUUID())
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void get_by_id_endpoint_permits_unauthenticated_requests() {
        ResponseEntity<String> response = client.get()
                .uri("/internal/tariffs/{id}", UUID.randomUUID())
                .retrieve()
                .toEntity(String.class);

        // /internal/tariffs/** is an internal, system-to-system lookup (no PII, no ADMIN-only
        // data) and is permitAll, mirroring customer-service's /internal/customers pattern. An
        // unknown id still resolves through security to the handler and returns 404, not 401.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void old_public_by_id_path_now_requires_auth() {
        // The route moved to /internal/tariffs/{id} (tech-lead ruling 2026-07-06, tariff
        // endpoint internal-surface fix). /api/v1/tariffs/by-id/** is no longer registered, so it
        // falls through to .anyRequest().authenticated() -> 401 for an unauthenticated caller.
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/tariffs/by-id/{id}", UUID.randomUUID())
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    /**
     * Persists a tariff directly in {@link com.telco.catalog.domain.model.TariffStatus#DRAFT}
     * state, bypassing the create endpoint (which now activates on creation per the Sprint 07
     * spec). Used to test the ACTIVE-only read guard on the query side.
     */
    private Tariff persistDraftTariff(String code) {
        Tariff tariff = Tariff.create(code, "Test Tariff " + code, TariffType.POSTPAID,
                new BigDecimal("49.99"), "TRY", 500, 100, 10240, null,
                Instant.parse("2026-01-01T00:00:00Z"), null);
        return tariffRepository.save(tariff);
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
