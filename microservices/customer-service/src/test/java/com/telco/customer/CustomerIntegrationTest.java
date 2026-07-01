package com.telco.customer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.infrastructure.storage.DocumentStorage;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack integration test for customer-service (feature 6.4.1, ADR-013).
 *
 * <p>Uses a Testcontainers Postgres and HMAC-signed JWTs from {@link JwtService#issue} so no
 * Keycloak or config-server is needed. {@link OutboxService} and {@link DocumentStorage} are mocked:
 * the outbox write-side is JDBC-only (no Kafka), and document binaries are not stored in this test.
 * The rest — Mediator pipeline, TransactionBehavior, Flyway schema, Spring Security, PII crypto, and
 * audit logging — run against a real database.
 *
 * <p>Covers: FR-01 (registration), FR-02 (KYC approve/reject), FR-03 (address), FR-04 (soft-delete),
 * plus the PII assertions from NFR-06 and AC-01 steps 1-3.
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
class CustomerIntegrationTest {

    private static final String VALID_TCKN = "10000000146";
    private static final String INVALID_TCKN = "12345678901";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    OutboxService outboxService;

    @MockitoBean
    DocumentStorage documentStorage;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    RequestSpecification spec;
    String customerToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM addresses");
        jdbc.execute("DELETE FROM customers");

        customerToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("CUSTOMER"));
        adminToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("ADMIN"));

        spec = new RequestSpecBuilder()
                .setPort(port)
                .setContentType(ContentType.JSON)
                .build();

        when(documentStorage.store(any(), any(), any())).thenReturn("kyc/doc.png");
    }

    // --- Auth boundary ---

    @Test
    void unauthenticated_request_returns_401() {
        given(spec)
                .post("/api/v1/customers")
                .then()
                .statusCode(401);
    }

    // --- FR-01: Customer registration ---

    @Test
    void registration_with_valid_tckn_returns_201_pending() {
        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body(registerBody("Ada", "Lovelace", VALID_TCKN))
                .post("/api/v1/customers")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.status", equalTo("PENDING"))
                .body("data.identityNumberMasked", matchesPattern("\\*+\\d{4}"))
                .body("data.identityNumberMasked", not(containsString(VALID_TCKN)));

        verify(outboxService).publish(eq("customer"), any(), eq("customer.registered.v1"), any());
    }

    @Test
    void registration_with_invalid_tckn_returns_400() {
        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body(registerBody("Test", "User", INVALID_TCKN))
                .post("/api/v1/customers")
                .then()
                .statusCode(400);
    }

    // --- FR-03 (GET + PUT) ---

    @Test
    void get_customer_returns_masked_pii() {
        String id = doRegister("Ada", "Lovelace", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .get("/api/v1/customers/" + id)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(id))
                .body("data.firstName", equalTo("Ada"))
                .body("data.identityNumberMasked", matchesPattern("\\*+\\d{4}"))
                .body("data.identityNumberMasked", not(containsString(VALID_TCKN)));
    }

    @Test
    void get_unknown_customer_returns_404() {
        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .get("/api/v1/customers/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void update_customer_returns_200_and_publishes_event() {
        String id = doRegister("Ada", "Lovelace", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body("""
                        {"firstName": "Grace", "lastName": "Hopper", "dateOfBirth": "1985-12-09"}
                        """)
                .put("/api/v1/customers/" + id)
                .then()
                .statusCode(200)
                .body("data.firstName", equalTo("Grace"))
                .body("data.lastName", equalTo("Hopper"));

        verify(outboxService).publish(eq("customer"), eq(id), eq("customer.updated.v1"), any());
    }

    // --- PII assertion: identity number is ciphertext in the DB ---

    @Test
    void identity_number_is_ciphertext_in_db_not_plaintext() {
        doRegister("Grace", "Hopper", VALID_TCKN);

        String stored = jdbc.queryForObject(
                "SELECT identity_number_enc FROM customers LIMIT 1", String.class);

        assertThat(stored).isNotNull();
        assertThat(stored).isNotEqualTo(VALID_TCKN);
    }

    // --- FR-04: Soft-delete ---

    @Test
    void soft_delete_returns_200_and_subsequent_get_returns_404() {
        String id = doRegister("Edsger", "Dijkstra", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .delete("/api/v1/customers/" + id)
                .then()
                .statusCode(200);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .get("/api/v1/customers/" + id)
                .then()
                .statusCode(404);

        // Row retained with deleted_at set (GDPR/KVKK: soft-delete only).
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE id = CAST(? AS uuid) AND deleted_at IS NOT NULL",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    // --- FR-02: KYC workflow ---

    @Test
    void kyc_approve_without_admin_role_returns_403() {
        String id = doRegister("Alan", "Turing", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .post("/api/v1/customers/" + id + "/kyc/approve")
                .then()
                .statusCode(403);
    }

    @Test
    void kyc_approve_with_admin_transitions_to_active_publishes_event_and_writes_audit() {
        String id = doRegister("Alan", "Turing", VALID_TCKN);
        doUploadDocument(id);

        given(spec)
                .header("Authorization", "Bearer " + adminToken)
                .post("/api/v1/customers/" + id + "/kyc/approve")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("ACTIVE"));

        verify(outboxService).publish(eq("customer"), eq(id), eq("customer.kyc-approved.v1"), any());

        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'CUSTOMER_KYC_APPROVED'",
                Integer.class, id);
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    void kyc_reject_with_admin_transitions_to_rejected_and_publishes_event() {
        String id = doRegister("Donald", "Knuth", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + adminToken)
                .body("""
                        {"reason": "Document photo unreadable"}
                        """)
                .post("/api/v1/customers/" + id + "/kyc/reject")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("REJECTED"));

        verify(outboxService).publish(eq("customer"), eq(id), eq("customer.kyc-rejected.v1"), any());
    }

    // --- FR-03: Address management ---

    @Test
    void adding_second_default_address_unsets_first() {
        String id = doRegister("Linus", "Torvalds", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body(addressBody("Istiklal Cad. 1", "Istanbul", "Beyoglu", "34433", true))
                .post("/api/v1/customers/" + id + "/addresses")
                .then()
                .statusCode(201)
                .body("data.isDefault", equalTo(true));

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body(addressBody("Bagdat Cad. 5", "Istanbul", "Kadikoy", "34710", true))
                .post("/api/v1/customers/" + id + "/addresses")
                .then()
                .statusCode(201);

        // Exactly one default must remain.
        Integer defaultCount = jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE customer_id = CAST(? AS uuid) AND is_default = true",
                Integer.class, id);
        assertThat(defaultCount).isEqualTo(1);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .get("/api/v1/customers/" + id + "/addresses")
                .then()
                .statusCode(200)
                .body("data.size()", equalTo(2));
    }

    // --- Document upload (FR-03, AC-01 step 2) ---

    @Test
    void document_upload_returns_201_with_metadata() {
        String id = doRegister("Tim", "Berners-Lee", VALID_TCKN);

        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .contentType("multipart/form-data")
                .multiPart("file", "id-card.png", "fake-png-bytes".getBytes(), "image/png")
                .queryParam("type", "ID_CARD")
                .post("/api/v1/customers/" + id + "/documents")
                .then()
                .statusCode(201)
                .body("data.type", equalTo("ID_CARD"))
                .body("data.fileRef", notNullValue());
    }

    // --- Helpers ---

    private String doRegister(String firstName, String lastName, String tckn) {
        return given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .body(registerBody(firstName, lastName, tckn))
                .post("/api/v1/customers")
                .then()
                .statusCode(201)
                .extract().path("data.id");
    }

    private void doUploadDocument(String customerId) {
        given(spec)
                .header("Authorization", "Bearer " + customerToken)
                .contentType("multipart/form-data")
                .multiPart("file", "passport.pdf", "fake-pdf-bytes".getBytes(), "application/pdf")
                .queryParam("type", "PASSPORT")
                .post("/api/v1/customers/" + customerId + "/documents")
                .then()
                .statusCode(201);
    }

    private static String registerBody(String firstName, String lastName, String tckn) {
        return """
                {
                    "type": "INDIVIDUAL",
                    "firstName": "%s",
                    "lastName": "%s",
                    "identityNumber": "%s",
                    "dateOfBirth": "1990-01-01"
                }
                """.formatted(firstName, lastName, tckn);
    }

    private static String addressBody(String line1, String city, String district,
                                      String postalCode, boolean isDefault) {
        return """
                {
                    "line1": "%s",
                    "city": "%s",
                    "district": "%s",
                    "postalCode": "%s",
                    "isDefault": %b
                }
                """.formatted(line1, city, district, postalCode, isDefault);
    }
}
