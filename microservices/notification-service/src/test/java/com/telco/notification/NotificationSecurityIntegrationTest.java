package com.telco.notification;

import com.telco.platform.starter.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the identity-to-customer linkage fix (ADR-011, sprint-14 14.1.1 ruling step 6) for the
 * {@code #userId == @currentUserProvider.currentUser().customerId()} clause on
 * {@link com.telco.notification.api.NotificationController}.
 *
 * <p>{@link JwtService#issue} (the only token-minting API this test may use without touching
 * starter-security, which is out of this agent's authority) never sets a {@code customerId} claim,
 * so every SUBSCRIBER token minted here represents the "unlinked subscriber" edge case the ruling's
 * null-safety requirement calls out: a null resolved {@code customerId} must never accidentally
 * satisfy the ownership check, even when the caller supplies a {@code userId} path variable equal to
 * their own raw JWT subject (the exact value the old, pre-fix comparison against
 * {@code authentication.name} would have wrongly accepted). The "real customerId claim matches"
 * success path is proven end-to-end by the acceptance suite (ruling step 7) against a real seeded
 * Keycloak identity carrying the linked claim.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class NotificationSecurityIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @Autowired private JwtService jwtService;

    @LocalServerPort int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { /* never throw */ })
                .build();
    }

    @Test
    void unlinked_subscriber_requesting_own_subject_as_user_id_is_denied_not_accidentally_allowed() {
        // Old, pre-fix comparison was "#userId == authentication.name": a SUBSCRIBER passing their
        // own JWT subject as the userId path variable would have satisfied it. The fixed comparison
        // resolves against the linked customerId claim (absent here), which must never be treated as
        // a match against anything, including the caller's own subject.
        String subject = UUID.randomUUID().toString();
        String token = jwtService.issue(subject, Set.of("SUBSCRIBER"));

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/notifications/users/{userId}/history", subject)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unlinked_subscriber_requesting_own_subject_for_preferences_is_denied() {
        String subject = UUID.randomUUID().toString();
        String token = jwtService.issue(subject, Set.of("SUBSCRIBER"));

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/notifications/users/{userId}/preferences", subject)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_bypass_still_allows_history_access_regardless_of_customer_id() {
        String adminToken = jwtService.issue(UUID.randomUUID().toString(), Set.of("ADMIN"));
        String anyUserId = UUID.randomUUID().toString();

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/notifications/users/{userId}/history", anyUserId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void subscriber_requesting_someone_elses_user_id_is_denied() {
        String subject = UUID.randomUUID().toString();
        String token = jwtService.issue(subject, Set.of("SUBSCRIBER"));
        String someoneElsesUserId = UUID.randomUUID().toString();

        ResponseEntity<String> response = client.get()
                .uri("/api/v1/notifications/users/{userId}/history", someoneElsesUserId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
