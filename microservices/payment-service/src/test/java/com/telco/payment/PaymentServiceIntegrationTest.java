package com.telco.payment;

import com.telco.payment.application.event.PaymentCompletedEvent;
import com.telco.payment.application.scheduler.PaymentRetryScheduler;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for payment-service (feature 8.6, ADR-013).
 *
 * Testcontainers Postgres; Mediator pipeline, Spring Security, and Flyway run real.
 * OutboxService and PspAdapter are mocked. Kafka listeners are disabled (auto-startup=false).
 * PaymentRetryScheduler is mocked to prevent background execution during tests.
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
class PaymentServiceIntegrationTest {

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
    PspAdapter pspAdapter;

    @MockitoBean
    PaymentRetryScheduler paymentRetryScheduler;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws PspException {
        jdbcTemplate.execute("TRUNCATE TABLE payment_attempts, payments CASCADE");

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue("admin-user", Set.of("ADMIN"));
        customerToken = jwtService.issue("customer-user", Set.of("SUBSCRIBER"));

        when(pspAdapter.charge(any(), any(), any()))
                .thenReturn(new ChargeResult("TXN-" + UUID.randomUUID()));
        when(pspAdapter.refund(any(), any(), any()))
                .thenReturn(new ChargeResult("REFUND-" + UUID.randomUUID()));
    }

    @Test
    void unauthenticated_charge_returns_401() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(chargeJson(UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString()))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void customer_cannot_charge_returns_403() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chargeJson(UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString()))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_charges_payment_psp_success_returns_201_completed() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String paymentRequestId = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> response = chargePayment(orderId, customerId, "49.99", paymentRequestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("COMPLETED");
        assertThat(data.get("orderId")).isEqualTo(orderId.toString());
        assertThat(data.get("id")).isNotNull();
    }

    @Test
    void admin_charges_payment_with_invoice_id_propagates_it_to_completed_event() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        String paymentRequestId = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "orderId": "%s",
                          "customerId": "%s",
                          "amount": 49.99,
                          "paymentRequestId": "%s",
                          "invoiceId": "%s"
                        }
                        """.formatted(orderId, customerId, paymentRequestId, invoiceId))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).get("status")).isEqualTo("COMPLETED");

        // The billing-service PaymentCompletedBillingConsumer only fires MarkInvoicePaidCommand when
        // payment.completed.v1 carries a non-null invoiceId - verify the outbox payload has it.
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.completed.v1"),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(PaymentCompletedEvent.class);
        PaymentCompletedEvent event = (PaymentCompletedEvent) payloadCaptor.getValue();
        assertThat(event.invoiceId()).isEqualTo(invoiceId.toString());
    }

    @Test
    void admin_charges_payment_without_invoice_id_leaves_it_null_on_completed_event() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String paymentRequestId = UUID.randomUUID().toString();

        chargePayment(orderId, customerId, "49.99", paymentRequestId);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("payment"), anyString(), eq("payment.completed.v1"),
                payloadCaptor.capture());
        PaymentCompletedEvent event = (PaymentCompletedEvent) payloadCaptor.getValue();
        assertThat(event.invoiceId()).isNull();
    }

    @Test
    void admin_charges_payment_psp_failure_returns_201_failed() throws PspException {
        when(pspAdapter.charge(any(), any(), any()))
                .thenThrow(new PspException("Card declined"));

        ResponseEntity<Map<String, Object>> response = chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).get("status")).isEqualTo("FAILED");
        assertThat(data(response).get("attemptCount")).isEqualTo(1);
    }

    @Test
    void idempotent_charge_returns_existing_completed_payment() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String paymentRequestId = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> first = chargePayment(orderId, customerId, "49.99", paymentRequestId);
        ResponseEntity<Map<String, Object>> second = chargePayment(orderId, customerId, "49.99", paymentRequestId);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(first).get("id")).isEqualTo(data(second).get("id"));
        assertThat(data(second).get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void get_payment_by_id_returns_200() {
        String paymentId = (String) data(chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString())).get("id");

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("id")).isEqualTo(paymentId);
    }

    @Test
    void customer_can_get_payment_by_id() {
        String paymentId = (String) data(chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString())).get("id");

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void get_payment_by_order_id_returns_200() {
        UUID orderId = UUID.randomUUID();
        chargePayment(orderId, UUID.randomUUID(), "49.99", UUID.randomUUID().toString());

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/payments/order/" + orderId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("orderId")).isEqualTo(orderId.toString());
    }

    @Test
    void get_nonexistent_payment_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/payments/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void admin_refunds_completed_payment_returns_200_refunded() {
        String paymentId = (String) data(chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString())).get("id");

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/payments/" + paymentId + "/refund")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"reason": "Customer request"}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("status")).isEqualTo("REFUNDED");
    }

    @Test
    void refund_failed_payment_returns_422() throws PspException {
        when(pspAdapter.charge(any(), any(), any()))
                .thenThrow(new PspException("Card declined"));

        String paymentId = (String) data(chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString())).get("id");

        ResponseEntity<String> response = client.post()
                .uri("/api/v1/payments/" + paymentId + "/refund")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"reason": "Test refund of failed payment"}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
    }

    @Test
    void customer_cannot_refund_returns_403() {
        String paymentId = (String) data(chargePayment(
                UUID.randomUUID(), UUID.randomUUID(), "49.99", UUID.randomUUID().toString())).get("id");

        ResponseEntity<String> response = client.post()
                .uri("/api/v1/payments/" + paymentId + "/refund")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"reason": "Unauthorized refund attempt"}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> chargePayment(UUID orderId, UUID customerId,
                                                               String amount, String paymentRequestId) {
        return client.post()
                .uri("/api/v1/payments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chargeJson(orderId, customerId, amount, paymentRequestId))
                .retrieve()
                .toEntity(MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private static String chargeJson(UUID orderId, UUID customerId, String amount, String paymentRequestId) {
        return """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "amount": %s,
                  "paymentRequestId": "%s"
                }
                """.formatted(orderId, customerId, amount, paymentRequestId);
    }
}
