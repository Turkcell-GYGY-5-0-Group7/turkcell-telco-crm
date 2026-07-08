package com.telco.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the gateway authentication flow (task 5.3.2): a valid Keycloak-shaped
 * JWT is accepted and its identity is propagated downstream as X-User-Id / X-User-Roles, while a
 * request without a token is rejected with 401 in the ApiResult error envelope (FR-IAM-02/03).
 *
 * The real JWKS decoder is bypassed via spring-security-test's jwt() post-processor, so the test is
 * deterministic and needs no running Keycloak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EchoController.class)
class GatewayAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/echo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void validTokenReachesProtectedRouteWithIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/echo")
                        .with(jwt().jwt(token -> token
                                .subject("user-9")
                                .claim("roles", List.of("ADMIN", "SUBSCRIBER"))
                                .claim("customerId", "customer-42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-9"))
                .andExpect(jsonPath("$.roles").value("ADMIN,SUBSCRIBER"))
                .andExpect(jsonPath("$.customerId").value("customer-42"));
    }

    @Test
    void tokenWithoutCustomerIdClaimOmitsCustomerIdHeaderEvenIfSpoofed() throws Exception {
        mockMvc.perform(get("/api/v1/echo")
                        .header("X-Customer-Id", "spoofed-customer")
                        .with(jwt().jwt(token -> token
                                .subject("user-9")
                                .claim("roles", List.of("SUBSCRIBER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-9"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }
}
