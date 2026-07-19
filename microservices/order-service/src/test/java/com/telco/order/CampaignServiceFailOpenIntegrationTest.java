package com.telco.order;

import com.telco.order.application.event.OrderCreatedEvent;
import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-level, hard proof of the sprint's most safety-critical exit criterion (Feature 21.5.3):
 * "campaign-service unavailability does not block order creation." Mirrors
 * {@code OrderServiceIntegrationTest}'s Testcontainers setup but, like
 * {@link CampaignDiscountedOrderIntegrationTest}, leaves {@code CampaignServiceClient} wired as a
 * real Spring bean (real {@link RestClient}, real Resilience4j {@link CircuitBreaker}) rather than
 * mocking the client interface directly - this is what distinguishes this test from the existing
 * client-unit-test-level proof in {@code CampaignServiceClientTest} and the handler-unit-test-level
 * proof in {@code CreateOrderCommandHandlerTest}: here the full HTTP -&gt; mediator -&gt; handler -&gt;
 * Postgres -&gt; outbox path runs for real, with only campaign-service itself made unreachable.
 *
 * <p>Two independent failure modes are proven, matching the two the fail-open contract must survive
 * (ADR-027 Decision Section 4, {@code CampaignServiceClient} javadoc):
 * <ol>
 *   <li>campaign-service simply unreachable (connection refused on every call);</li>
 *   <li>the circuit breaker already OPEN (short-circuited, no call attempted at all).</li>
 * </ol>
 * In both cases the order must still be created successfully, priced at the full undiscounted
 * {@code monthlyFee}, with no {@code campaignId} recorded anywhere (response, persisted
 * {@code OrderItem}, or the outbox {@code order.created.v1} payload).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.jpa.open-in-view=false",
                // Guaranteed connection-refused: a privileged, never-listening port on every platform
                // this suite runs on (the same technique CampaignServiceClientTest already uses).
                "telco.clients.campaign-service.url=http://localhost:1"
        }
)
@ActiveProfiles("test")
@Testcontainers
class CampaignServiceFailOpenIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final UUID CUSTOMER_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
    private static final UUID TARIFF_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final BigDecimal MONTHLY_FEE = new BigDecimal("100.00");

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

    @Autowired
    @Qualifier("campaignServiceCircuitBreaker")
    CircuitBreaker campaignServiceCircuitBreaker;

    @LocalServerPort
    int port;

    private RestClient client;
    private String customerToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE saga_state, order_items, orders CASCADE");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        customerToken = jwtService.issue("user-alpha", Set.of("SUBSCRIBER"));

        when(customerServiceClient.getCustomer(any()))
                .thenReturn(new CustomerClientResponse(CUSTOMER_ID, "ACTIVE"));
        when(productCatalogServiceClient.getTariff(any()))
                .thenReturn(new TariffClientResponse(
                        TARIFF_ID, "POSTPAID-FO", "Postpaid Fail-Open", MONTHLY_FEE, "TRY", 1));
    }

    @AfterEach
    void resetCircuitBreaker() {
        // Restore CLOSED so this bean (a Spring-managed singleton, shared across every test method
        // running in this class's cached context) does not leak OPEN state into a sibling test.
        campaignServiceCircuitBreaker.transitionToClosedState();
    }

    @Test
    void order_creation_with_campaign_service_unreachable_still_succeeds_at_full_price() {
        assertOrderSucceedsAtUndiscountedPrice();
    }

    @Test
    void order_creation_with_circuit_breaker_open_still_succeeds_at_full_price() {
        campaignServiceCircuitBreaker.transitionToOpenState();
        assertThat(campaignServiceCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertOrderSucceedsAtUndiscountedPrice();
    }

    private void assertOrderSucceedsAtUndiscountedPrice() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(orderJson())
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = data(response);
        String orderId = (String) data.get("id");
        assertThat(data.get("status")).isEqualTo("PENDING");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(1);
        assertThat(new BigDecimal(items.get(0).get("unitPrice").toString()))
                .isEqualByComparingTo(MONTHLY_FEE);
        assertThat(items.get(0).get("campaignId")).isNull();

        // Verify directly against the persisted OrderItem row in Postgres.
        Map<String, Object> persistedItem = jdbcTemplate.queryForMap(
                "SELECT unit_price, campaign_id FROM order_items WHERE order_id = ?::uuid", orderId);
        assertThat((BigDecimal) persistedItem.get("unit_price")).isEqualByComparingTo(MONTHLY_FEE);
        assertThat(persistedItem.get("campaign_id")).isNull();

        // Verify the outbox order.created.v1 payload also carries the undiscounted price, no campaignId.
        org.mockito.ArgumentCaptor<Object> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("order"), eq(orderId), eq("order.created.v1"), payloadCaptor.capture());
        OrderCreatedEvent event = (OrderCreatedEvent) payloadCaptor.getValue();
        assertThat(event.items().get(0).unitPrice()).isEqualByComparingTo(MONTHLY_FEE);
        assertThat(event.items().get(0).campaignId()).isNull();
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private static String orderJson() {
        return """
                {
                  "customerId": "%s",
                  "items": [{ "tariffId": "%s", "quantity": 1 }]
                }
                """.formatted(CUSTOMER_ID, TARIFF_ID);
    }
}
