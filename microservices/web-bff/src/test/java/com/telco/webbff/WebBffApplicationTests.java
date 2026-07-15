package com.telco.webbff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots web-bff without live infrastructure (config-server and Eureka disabled) and proves the
 * locally verifiable part of 16.1.1 acceptance criterion 1: the application context loads and
 * {@code /actuator/health} reports UP. Registration with discovery-server is a live-stack concern,
 * validated when the platform runs (per the Sprint 15 precedent). Gateway-trust mode is enabled so
 * the security filter needs no signing key, matching web-bff's gateway-behind-trust deployment.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                // Supplied centrally by config-server in a running stack; the test disables config
                // import, so it must be set here (Spring Cloud 2025.1.0 + Boot 4.1.0 are compatible;
                // the verifier's version table lags). See microservices/configs/application.yml.
                "spring.cloud.compatibility-verifier.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "telco.gateway.base-url=http://localhost:8080",
                "telco.platform.security.gateway-trust.enabled=true"
        }
)
@ActiveProfiles("test")
class WebBffApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        // Context startup is the assertion; a failure here fails the test.
    }

    @Test
    void health_endpoint_reports_up() {
        String body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("UP");
    }
}
