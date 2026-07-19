package com.telco.order.infrastructure.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link CampaignServiceClient}'s fail-open contract (Feature 21.3.2, ADR-027
 * Decision Section 4). No Spring context: a real loopback {@link HttpServer} stands in for
 * campaign-service for the happy path; an unreachable port and a manually-forced-OPEN circuit
 * breaker exercise the two failure modes the client must swallow.
 */
class CampaignServiceClientTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static CircuitBreaker newCircuitBreaker() {
        return CircuitBreaker.of("campaign-service-test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    @Test
    void reachable_and_eligible_returns_the_real_discount_decision() throws IOException {
        UUID campaignId = UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/campaigns/validate", exchange -> {
            String body = """
                    {"success":true,"data":{"eligible":true,"campaignId":"%s",\
                    "discountType":"PERCENTAGE","discountValue":25.00,"reason":null}}\
                    """.formatted(campaignId);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        CampaignServiceClient client = new CampaignServiceClient(restClient, newCircuitBreaker());

        CampaignValidationResponse response = client.validate(CUSTOMER_ID, "P-001", "SUMMER25");

        assertThat(response.eligible()).isTrue();
        assertThat(response.campaignId()).isEqualTo(campaignId);
        assertThat(response.discountType()).isEqualTo("PERCENTAGE");
        assertThat(response.discountValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void unreachable_campaign_service_returns_sentinel_without_throwing() {
        // Port 1 is a privileged, never-listening port on every platform this suite runs on -
        // guaranteed connection refused, no server to stand up.
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:1")
                .build();
        CampaignServiceClient client = new CampaignServiceClient(restClient, newCircuitBreaker());

        assertThatCode(() -> {
            CampaignValidationResponse response = client.validate(CUSTOMER_ID, "P-001", null);
            assertThat(response).isEqualTo(CampaignServiceClient.NOT_ELIGIBLE_SENTINEL);
            assertThat(response.eligible()).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    void open_circuit_breaker_returns_sentinel_without_throwing() {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:1")
                .build();
        CircuitBreaker circuitBreaker = newCircuitBreaker();
        circuitBreaker.transitionToOpenState();
        CampaignServiceClient client = new CampaignServiceClient(restClient, circuitBreaker);

        assertThatCode(() -> {
            CampaignValidationResponse response = client.validate(CUSTOMER_ID, "P-001", null);
            assertThat(response).isEqualTo(CampaignServiceClient.NOT_ELIGIBLE_SENTINEL);
        }).doesNotThrowAnyException();
    }
}
