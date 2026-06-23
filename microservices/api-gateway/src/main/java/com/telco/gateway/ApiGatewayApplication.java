package com.telco.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway - the single secured entry point (ADR-011, ADR-015). Routing, Keycloak JWT
 * validation, identity-header propagation, correlation, and rate limiting are added during
 * implementation; route configuration is served centrally by config-server. Skeleton only.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
