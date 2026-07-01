package com.telco.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.path;
import static org.springframework.web.servlet.function.RouterFunctions.route;

/**
 * Route definitions are declared in the config-server YAML
 * (microservices/configs/api-gateway/application.yml, 4.3.1).
 * The GatewayMvcPropertiesBeanDefinitionRegistrar registers them automatically
 * via spring.cloud.gateway.server.webmvc.routes.
 *
 * <p>The one exception is the local 404 handler below. It is NOT a proxy route - it is a
 * terminal sink for the {@code internal-deny-route} declared in YAML, which forwards
 * {@code /internal/**} to {@code forward:/__gateway_blocked}. This enforces the ADR-011
 * network boundary: {@code /internal/**} is a service-to-service-only surface (e.g. order-service
 * {@code GET /internal/orders/{orderId}} used by the onboarding saga, Sprint 09 Feature 9.4) and
 * must never be reachable through the gateway. The YAML domain routes are all scoped to
 * {@code /api/v1/**}, so {@code /internal/**} is already unrouted; this handler makes the deny
 * explicit and fail-safe so a future broad route change cannot accidentally expose it.
 */
@Configuration
public class GatewayRouteConfig {
    // Proxy routes live in YAML, not Java, to keep them centrally configurable.

    /**
     * Terminal 404 sink for {@code /internal/**}. The YAML {@code internal-deny-route} forwards to
     * {@code /__gateway_blocked}; this RouterFunction answers it with a bare 404 and never proxies
     * downstream. {@code /__gateway_blocked} is an internal forward target only - it is not a
     * routable external path.
     */
    @Bean
    public RouterFunction<ServerResponse> internalDenyRouterFunction() {
        return route(path("/__gateway_blocked"),
                request -> ServerResponse.status(HttpStatus.NOT_FOUND).build());
    }
}
