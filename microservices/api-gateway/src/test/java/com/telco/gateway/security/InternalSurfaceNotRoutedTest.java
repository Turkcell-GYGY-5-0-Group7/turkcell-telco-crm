package com.telco.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the ADR-011 network boundary for the service-to-service-only {@code /internal/**} surface
 * (e.g. order-service {@code GET /internal/orders/{orderId}}, Sprint 09 Feature 9.4): the gateway
 * never routes {@code /internal/**} to a downstream service. The highest-priority
 * {@code internal-deny-route} matches it and forwards to a local 404 sink
 * (GatewayRouteConfig#internalDenyRouterFunction), so it is unreachable through the gateway whether
 * or not the caller presents a token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalSurfaceNotRoutedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void internalPathIsBlockedWith404WithoutToken() throws Exception {
        mockMvc.perform(get("/internal/orders/order-123"))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalPathIsBlockedWith404EvenWithValidToken() throws Exception {
        // A valid JWT must not buy access to /internal/**; the boundary is the network edge, not auth.
        mockMvc.perform(get("/internal/orders/order-123")
                        .with(jwt().jwt(token -> token
                                .subject("user-9")
                                .claim("roles", List.of("ADMIN")))))
                .andExpect(status().isNotFound());
    }
}
