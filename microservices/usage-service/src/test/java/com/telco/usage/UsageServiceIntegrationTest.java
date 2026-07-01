package com.telco.usage;

import com.telco.platform.inbox.InboxService;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class UsageServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @MockitoBean private OutboxService outboxService;
    @MockitoBean private InboxService inboxService;

    @Autowired private JwtService jwtService;

    @LocalServerPort int port;

    private RestClient client;
    private String customerToken;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();

        customerToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("CUSTOMER"));
    }

    @Test
    void get_quota_returns_404_for_unknown_subscription() {
        UUID unknown = UUID.randomUUID();
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/usage/subscriptions/{subscriptionId}/quota", unknown)
                .header("Authorization", "Bearer " + customerToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void get_usage_history_requires_authentication() {
        UUID subscriptionId = UUID.randomUUID();
        ResponseEntity<String> response = client.get()
                .uri("/api/v1/usage/subscriptions/{subscriptionId}/history", subscriptionId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void aggregate_usage_requires_admin_role() {
        UUID subscriptionId = UUID.randomUUID();
        // Send a valid body so argument resolution succeeds and @PreAuthorize("hasRole('ADMIN')")
        // runs — a CUSTOMER-role JWT must be rejected with 403.
        ResponseEntity<String> response = client.post()
                .uri("/api/v1/usage/subscriptions/{subscriptionId}/aggregate", subscriptionId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"periodStart": "2026-06-01T00:00:00Z", "periodEnd": "2026-07-01T00:00:00Z"}
                        """)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
