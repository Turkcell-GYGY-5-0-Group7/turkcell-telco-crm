package com.telco.identity;

import com.telco.identity.infrastructure.AuditLogRepository;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Integration tests for identity-service (feature 5.7.1, ADR-013).
 *
 * <p>Uses a Testcontainers Postgres and HMAC-signed JWTs from {@link JwtService#issue} so no
 * Keycloak instance is needed. {@link KeycloakAdminClient} and {@link OutboxService} are mocked;
 * the rest — Mediator pipeline, TransactionBehavior, audit log, Spring Security — runs real.
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
class IdentityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    KeycloakAdminClient keycloakAdminClient;

    @MockitoBean
    OutboxService outboxService;

    @Autowired
    JwtService jwtService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @LocalServerPort
    int port;

    private RestClient client;
    private String adminToken;
    private String customerToken;
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        adminToken = jwtService.issue(ACTOR_ID.toString(), Set.of("ADMIN"));
        customerToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("CUSTOMER"));

        when(keycloakAdminClient.createUser(anyString(), anyString()))
                .thenReturn(UUID.randomUUID().toString());
        doNothing().when(keycloakAdminClient).assignRealmRoles(anyString(), anySet());
        doNothing().when(keycloakAdminClient).removeRealmRoles(anyString(), anySet());
    }

    @Test
    void unauthenticated_request_returns_401() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/users")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void non_admin_token_on_admin_endpoint_returns_403() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_creates_user_returns_201_with_correct_body() {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "testuser", "email": "testuser@example.com"}
                        """)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("username")).isEqualTo("testuser");
        assertThat(data.get("email")).isEqualTo("testuser@example.com");
        assertThat(data.get("id")).isNotNull();
    }

    @Test
    void audit_log_row_written_after_user_creation() {
        ResponseEntity<String> r = client.post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "audituser", "email": "audituser@example.com"}
                        """)
                .retrieve()
                .toEntity(String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "USER_CREATED".equals(log.getAction())
                        && "User".equals(log.getEntity()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_gets_user_returns_200() {
        ResponseEntity<Map> created = client.post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "getuser", "email": "getuser@example.com"}
                        """)
                .retrieve()
                .toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) ((Map<String, Object>) created.getBody().get("data")).get("id");

        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/users/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_lists_users_returns_200() {
        client.post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "listuser", "email": "listuser@example.com"}
                        """)
                .retrieve()
                .toEntity(String.class);

        ResponseEntity<Map> response = client.get()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("data")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_assigns_roles_returns_200_and_audit_written() {
        ResponseEntity<Map> created = client.post()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"username": "roleuser", "email": "roleuser@example.com"}
                        """)
                .retrieve()
                .toEntity(Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) ((Map<String, Object>) created.getBody().get("data")).get("id");

        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/users/" + userId + "/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"roleNames": ["CUSTOMER"]}
                        """)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "ROLES_ASSIGNED".equals(log.getAction())
                        && userId.equals(log.getEntityId()));
    }

    @Test
    void missing_user_returns_404() {
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
