package com.telco.order;

import com.sun.net.httpserver.HttpServer;
import com.telco.order.application.event.OrderCreatedEvent;
import com.telco.order.infrastructure.client.CustomerClientResponse;
import com.telco.order.infrastructure.client.CustomerServiceClient;
import com.telco.order.infrastructure.client.ProductCatalogServiceClient;
import com.telco.order.infrastructure.client.TariffClientResponse;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-level proof of Feature 21.3.3's discounted-pricing exit criterion (Feature 21.5.3,
 * ADR-013), mirroring {@code OrderServiceIntegrationTest}'s Testcontainers setup. Unlike
 * {@code OrderServiceIntegrationTest} (which mocks {@code CampaignServiceClient} directly to hold
 * pricing behavior constant for its unrelated assertions), this test leaves {@code CampaignServiceClient}
 * as a real Spring bean wired to a real {@link RestClient}/circuit breaker, pointed via
 * {@code telco.clients.campaign-service.url} at a loopback {@link HttpServer} standing in for a
 * genuinely reachable, eligible campaign-service - the same stub-server technique
 * {@code CampaignServiceClientTest} already uses at the unit level, now exercised through the full
 * HTTP -&gt; mediator -&gt; handler -&gt; Postgres -&gt; outbox path.
 *
 * <p>Proves: a full order-creation call with campaign-service healthy and eligible produces a
 * discounted {@code OrderItem.unitPrice} (verified directly against {@code order_items} in Postgres)
 * and the same discounted price/campaignId in the outbox {@code order.created.v1} payload (21.5.3
 * acceptance criteria).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.jpa.open-in-view=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class CampaignDiscountedOrderIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final UUID CUSTOMER_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID TARIFF_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID CAMPAIGN_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final BigDecimal MONTHLY_FEE = new BigDecimal("100.00");
    private static final BigDecimal DISCOUNT_PERCENTAGE = new BigDecimal("20.00");
    private static final BigDecimal EXPECTED_DISCOUNTED_PRICE = new BigDecimal("80.00");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final HttpServer CAMPAIGN_STUB = startCampaignStub();

    private static HttpServer startCampaignStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/campaigns/validate", exchange -> {
                String body = """
                        {"success":true,"data":{"eligible":true,"campaignId":"%s",\
                        "discountType":"PERCENTAGE","discountValue":%s,"reason":null}}\
                        """.formatted(CAMPAIGN_ID, DISCOUNT_PERCENTAGE);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stopCampaignStub() {
        CAMPAIGN_STUB.stop(0);
    }

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Real CampaignServiceClient bean (not mocked) targets the loopback stub above - genuinely
        // reachable, over real HTTP, through the real Resilience4j circuit breaker.
        registry.add("telco.clients.campaign-service.url",
                () -> "http://localhost:" + CAMPAIGN_STUB.getAddress().getPort());
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
                        TARIFF_ID, "POSTPAID-DISC", "Postpaid Discountable", MONTHLY_FEE, "TRY", 1));
    }

    @Test
    void order_creation_with_eligible_campaign_is_priced_at_the_discount_end_to_end() {
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

        @SuppressWarnings("unchecked")
        var items = (java.util.List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(1);
        assertThat(new BigDecimal(items.get(0).get("unitPrice").toString()))
                .isEqualByComparingTo(EXPECTED_DISCOUNTED_PRICE);
        assertThat(items.get(0).get("campaignId")).isEqualTo(CAMPAIGN_ID.toString());

        // Verify directly against the persisted OrderItem row in Postgres, not just the HTTP response.
        Map<String, Object> persistedItem = jdbcTemplate.queryForMap(
                "SELECT unit_price, campaign_id FROM order_items WHERE order_id = ?::uuid", orderId);
        assertThat((BigDecimal) persistedItem.get("unit_price"))
                .isEqualByComparingTo(EXPECTED_DISCOUNTED_PRICE);
        assertThat(persistedItem.get("campaign_id").toString()).isEqualTo(CAMPAIGN_ID.toString());

        // Verify the outbox order.created.v1 payload carries the same discounted price/campaignId.
        org.mockito.ArgumentCaptor<Object> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("order"), eq(orderId), eq("order.created.v1"), payloadCaptor.capture());
        OrderCreatedEvent event = (OrderCreatedEvent) payloadCaptor.getValue();
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().get(0).unitPrice()).isEqualByComparingTo(EXPECTED_DISCOUNTED_PRICE);
        assertThat(event.items().get(0).campaignId()).isEqualTo(CAMPAIGN_ID.toString());
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
                  "items": [{ "tariffId": "%s", "quantity": 1, "campaignCode": "DISCOUNT20" }]
                }
                """.formatted(CUSTOMER_ID, TARIFF_ID);
    }
}
