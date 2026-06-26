package com.telco.order;

import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for order-service (feature 8.6, ADR-013).
 *
 * Testcontainers Postgres; Mediator pipeline, Spring Security, and Flyway run real.
 * OutboxService, CustomerServiceClient, and ProductCatalogServiceClient are mocked.
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
class OrderServiceIntegrationTest {

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

    @MockitoBean
    CustomerServiceClient customerServiceClient;

    @MockitoBean
    ProductCatalogServiceClient productCatalogServiceClient;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;
    private String customerAlphaToken;
    private String customerBetaToken;

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TARIFF_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE saga_state, order_items, orders CASCADE");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue("admin-user", Set.of("ADMIN"));
        customerAlphaToken = jwtService.issue("user-alpha", Set.of("CUSTOMER"));
        customerBetaToken = jwtService.issue("user-beta", Set.of("CUSTOMER"));

        when(customerServiceClient.getCustomer(any()))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(any()))
                .thenReturn(new TariffClientResponse(TARIFF_ID, "POSTPAID-001", "Postpaid Basic", new BigDecimal("49.99"), "TRY"));
    }

    @Test
    void unauthenticated_create_returns_401() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderJson(CUSTOMER_ID, TARIFF_ID))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void customer_creates_order_returns_201() {
        ResponseEntity<Map<String, Object>> response = createOrder(customerAlphaToken, UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("PENDING");
        assertThat(data.get("customerId")).isEqualTo(CUSTOMER_ID.toString());
        assertThat(data.get("id")).isNotNull();
    }

    @Test
    void admin_creates_order_returns_201() {
        ResponseEntity<Map<String, Object>> response = createOrder(adminToken, UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).get("status")).isEqualTo("PENDING");
    }

    @Test
    void idempotent_create_returns_existing_order() {
        String iKey = UUID.randomUUID().toString();
        ResponseEntity<Map<String, Object>> first = createOrder(customerAlphaToken, iKey);
        ResponseEntity<Map<String, Object>> second = createOrder(customerAlphaToken, iKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(first).get("id")).isEqualTo(data(second).get("id"));
    }

    @Test
    void customer_can_get_own_order() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerAlphaToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("id")).isEqualTo(orderId);
    }

    @Test
    void customer_cannot_get_other_customers_order_returns_403() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerBetaToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_can_get_any_order() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("id")).isEqualTo(orderId);
    }

    @Test
    void get_nonexistent_order_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/orders/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customer_list_returns_only_own_orders() {
        createOrder(customerAlphaToken, UUID.randomUUID().toString());
        createOrder(customerBetaToken, UUID.randomUUID().toString());

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/orders/customer/" + CUSTOMER_ID)
                .header("Authorization", "Bearer " + customerAlphaToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) data(response).get("content");
        assertThat(content).hasSize(1);
    }

    @Test
    void admin_list_returns_orders_for_customer_id() {
        createOrder(customerAlphaToken, UUID.randomUUID().toString());
        createOrder(customerBetaToken, UUID.randomUUID().toString());

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/orders/customer/" + CUSTOMER_ID)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Object> content = (List<Object>) data(response).get("content");
        assertThat(content).hasSize(2);
    }

    @Test
    void customer_can_cancel_own_pending_order() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));

        ResponseEntity<Map<String, Object>> response = client.delete()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerAlphaToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void customer_cannot_cancel_other_customers_order_returns_403() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));

        ResponseEntity<String> response = client.delete()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerBetaToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancel_already_cancelled_order_returns_422() {
        String orderId = orderId(createOrder(customerAlphaToken, UUID.randomUUID().toString()));
        client.delete()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerAlphaToken)
                .retrieve()
                .toEntity(String.class);

        ResponseEntity<String> response = client.delete()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerAlphaToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> createOrder(String token, String idempotencyKey) {
        return client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderJson(CUSTOMER_ID, TARIFF_ID))
                .retrieve()
                .toEntity(MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private String orderId(ResponseEntity<Map<String, Object>> response) {
        return (String) data(response).get("id");
    }

    private static String orderJson(UUID customerId, UUID tariffId) {
        return """
                {
                  "customerId": "%s",
                  "items": [{ "tariffId": "%s", "quantity": 1 }]
                }
                """.formatted(customerId, tariffId);
    }
}
