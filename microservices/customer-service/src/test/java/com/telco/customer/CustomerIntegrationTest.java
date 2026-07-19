package com.telco.customer;

import com.telco.customer.application.event.CustomerRegisteredV1;
import com.telco.customer.infrastructure.storage.DocumentStorage;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private static final String VALID_VKN = "1234567890";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean OutboxService outboxService;
    @MockitoBean DocumentStorage documentStorage;

    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    @LocalServerPort int port;

    private RestClient client;
    private String customerToken;
    private String adminToken;
    private String agentToken;
    private String subscriberUserId;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM addresses");
        jdbc.execute("DELETE FROM customers");

        subscriberUserId = UUID.randomUUID().toString();
        customerToken = jwtService.issue(subscriberUserId, Set.of("SUBSCRIBER"));
        adminToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("ADMIN"));
        agentToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("CALL_CENTER_AGENT"));

        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        when(documentStorage.store(any(), any(), any())).thenReturn("kyc/doc.png");
    }

    // --- Auth boundary ---

    @Test
    void unauthenticated_request_returns_401() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- FR-01: Customer registration ---

    @Test
    void registration_with_valid_tckn_returns_201_pending() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Ada", "Lovelace", VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = data(response);
        assertThat(data.get("status")).isEqualTo("PENDING");
        String masked = (String) data.get("identityNumberMasked");
        assertThat(masked).matches("\\*+\\d{4}");
        assertThat(masked).doesNotContain(VALID_TCKN);

        verify(outboxService).publish(eq("customer"), any(), eq("customer.registered.v1"), any());
    }

    @Test
    void subscriber_self_registration_sets_registered_by_user_id_to_caller() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Ada", "Lovelace", VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.registeredByUserId()).isEqualTo(subscriberUserId);
    }

    @Test
    void subscriber_self_registration_with_keycloak_technical_roles_still_sets_registered_by_user_id() {
        // Regression test (Feature 14.4 end-to-end verification): a real Keycloak-Admin-API-created
        // user's token always additionally carries the realm's default composite role and its two
        // expanded client roles, on top of the application-level SUBSCRIBER role. The self-service
        // check must not be defeated by these Keycloak-internal roles - confirmed live against a real
        // Keycloak container where the exact-set-equality version of this check silently misclassified
        // every such caller as agent/dealer-assisted, permanently blocking self-service linkage.
        String realTokenShapeUserId = UUID.randomUUID().toString();
        String tokenWithKeycloakTechnicalRoles = jwtService.issue(realTokenShapeUserId,
                Set.of("SUBSCRIBER", "default-roles-telco-crm", "offline_access", "uma_authorization"));

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + tokenWithKeycloakTechnicalRoles)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Grace", "Realistic", VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.registeredByUserId()).isEqualTo(realTokenShapeUserId);
    }

    @Test
    void agent_assisted_registration_leaves_registered_by_user_id_null() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Grace", "Hopper", VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("customer"), any(), eq("customer.registered.v1"), payloadCaptor.capture());
        CustomerRegisteredV1 event = (CustomerRegisteredV1) payloadCaptor.getValue();
        assertThat(event.registeredByUserId()).isNull();
    }

    // --- FR-01 / feature 24.5: type-conditional TCKN/VKN validation and contact info ---

    @Test
    void corporate_registration_with_valid_vkn_returns_201() {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("CORPORATE", "Acme", "Telekom", VALID_VKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).get("type")).isEqualTo("CORPORATE");
    }

    @Test
    void corporate_registration_with_tckn_returns_400() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("CORPORATE", "Acme", "Telekom", VALID_TCKN))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registration_and_update_persist_and_return_contact_info() {
        ResponseEntity<Map<String, Object>> registered = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "type": "INDIVIDUAL",
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "identityNumber": "%s",
                            "dateOfBirth": "1990-01-01",
                            "email": "ada@example.com",
                            "phone": "+905321112233"
                        }
                        """.formatted(VALID_TCKN))
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(registered).get("email")).isEqualTo("ada@example.com");
        assertThat(data(registered).get("phone")).isEqualTo("+905321112233");
        String id = data(registered).get("id").toString();

        String storedEmail = jdbc.queryForObject(
                "SELECT email FROM customers WHERE id = CAST(? AS uuid)", String.class, id);
        assertThat(storedEmail).isEqualTo("ada@example.com");

        ResponseEntity<Map<String, Object>> updated = client.put()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "dateOfBirth": "1990-01-01",
                            "email": "countess@example.com",
                            "phone": "+905329998877"
                        }
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updated).get("email")).isEqualTo("countess@example.com");
        assertThat(data(updated).get("phone")).isEqualTo("+905329998877");
    }

    @Test
    void registration_with_malformed_contact_info_returns_400() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                            "type": "INDIVIDUAL",
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "identityNumber": "%s",
                            "dateOfBirth": "1990-01-01",
                            "email": "not-an-email",
                            "phone": "not-a-phone"
                        }
                        """.formatted(VALID_TCKN))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registration_with_invalid_tckn_returns_400() {
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Test", "User", INVALID_TCKN))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- FR-03 (GET + PUT) ---
    // GET/PUT by id are staff (ADMIN, CALL_CENTER_AGENT) or the OWNER of the record, resolved from
    // the linked customerId claim; DELETE stays ADMIN-only. The tokens minted here by
    // JwtService.issue carry no customerId claim (unlinked identities), so they exercise the staff
    // paths plus the unlinked-subscriber denial below. The owner paths and the non-owner/null-match
    // denials are covered by CustomerSelfOwnershipSecurityIntegrationTest.

    @Test
    void get_customer_returns_masked_pii() {
        String id = doRegister("Ada", "Lovelace", VALID_TCKN);

        ResponseEntity<Map<String, Object>> response = client.get()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = data(response);
        assertThat(data.get("id")).isEqualTo(id);
        assertThat(data.get("firstName")).isEqualTo("Ada");
        String masked = (String) data.get("identityNumberMasked");
        assertThat(masked).matches("\\*+\\d{4}");
        assertThat(masked).doesNotContain(VALID_TCKN);
    }

    @Test
    void get_unknown_customer_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/customers/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unlinked_subscriber_cannot_get_customer_by_id_returns_403() {
        // customerToken carries no customerId claim (unlinked identity): the ownership clause must
        // not match on null. See CustomerSelfOwnershipSecurityIntegrationTest for the linked cases.
        String id = doRegister("Ada", "Lovelace", VALID_TCKN);

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void update_customer_returns_200_and_publishes_event() {
        String id = doRegister("Ada", "Lovelace", VALID_TCKN);

        ResponseEntity<Map<String, Object>> response = client.put()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"firstName": "Grace", "lastName": "Hopper", "dateOfBirth": "1985-12-09"}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = data(response);
        assertThat(data.get("firstName")).isEqualTo("Grace");
        assertThat(data.get("lastName")).isEqualTo("Hopper");

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

        ResponseEntity<String> deleteResponse = client.delete()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> getResponse = client.get()
                .uri("/api/v1/customers/{id}", id)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE id = CAST(? AS uuid) AND deleted_at IS NOT NULL",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    // --- FR-02: KYC workflow ---

    @Test
    void kyc_approve_without_admin_role_returns_403() {
        String id = doRegister("Alan", "Turing", VALID_TCKN);

        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers/{id}/kyc/approve", id)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void kyc_approve_with_admin_transitions_to_active_publishes_event_and_writes_audit() {
        String id = doRegister("Alan", "Turing", VALID_TCKN);
        doUploadDocument(id);

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers/{id}/kyc/approve", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("status")).isEqualTo("ACTIVE");

        verify(outboxService).publish(eq("customer"), eq(id), eq("customer.kyc-approved.v1"), any());

        Integer auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'CUSTOMER_KYC_APPROVED'",
                Integer.class, id);
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    void kyc_reject_with_admin_transitions_to_rejected_and_publishes_event() {
        String id = doRegister("Donald", "Knuth", VALID_TCKN);

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers/{id}/kyc/reject", id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"reason": "Document photo unreadable"}
                        """)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).get("status")).isEqualTo("REJECTED");

        verify(outboxService).publish(eq("customer"), eq(id), eq("customer.kyc-rejected.v1"), any());
    }

    // --- FR-03: Address management ---

    @Test
    void adding_second_default_address_unsets_first() {
        String id = doRegister("Linus", "Torvalds", VALID_TCKN);

        ResponseEntity<Map<String, Object>> first = client.post()
                .uri("/api/v1/customers/{id}/addresses", id)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(addressBody("Istiklal Cad. 1", "Istanbul", "Beyoglu", "34433", true))
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(first).get("isDefault")).isEqualTo(true);

        client.post()
                .uri("/api/v1/customers/{id}/addresses", id)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(addressBody("Bagdat Cad. 5", "Istanbul", "Kadikoy", "34710", true))
                .retrieve()
                .toEntity(String.class);

        Integer defaultCount = jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE customer_id = CAST(? AS uuid) AND is_default = true",
                Integer.class, id);
        assertThat(defaultCount).isEqualTo(1);

        ResponseEntity<Map<String, Object>> list = client.get()
                .uri("/api/v1/customers/{id}/addresses", id)
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        java.util.List<?> dataList = (java.util.List<?>) list.getBody().get("data");
        assertThat(dataList).hasSize(2);
    }

    // --- Document upload (FR-03, AC-01 step 2) ---

    @Test
    void document_upload_returns_201_with_metadata() {
        String id = doRegister("Tim", "Berners-Lee", VALID_TCKN);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource("fake-png-bytes".getBytes(), "id-card.png"));

        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers/{id}/documents?type=ID_CARD", id)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = data(response);
        assertThat(data.get("type")).isEqualTo("ID_CARD");
        assertThat(data.get("fileRef")).isNotNull();
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private Map<?, ?> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<?, ?>) response.getBody().get("data");
    }

    private String doRegister(String firstName, String lastName, String tckn) {
        ResponseEntity<Map<String, Object>> response = client.post()
                .uri("/api/v1/customers")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody(firstName, lastName, tckn))
                .retrieve()
                .toEntity(MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return data(response).get("id").toString();
    }

    private void doUploadDocument(String customerId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource("fake-pdf-bytes".getBytes(), "passport.pdf"));

        ResponseEntity<String> response = client.post()
                .uri("/api/v1/customers/{id}/documents?type=PASSPORT", customerId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private static String registerBody(String firstName, String lastName, String tckn) {
        return registerBody("INDIVIDUAL", firstName, lastName, tckn);
    }

    private static String registerBody(String type, String firstName, String lastName,
                                       String identityNumber) {
        return """
                {
                    "type": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "identityNumber": "%s",
                    "dateOfBirth": "1990-01-01"
                }
                """.formatted(type, firstName, lastName, identityNumber);
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

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
