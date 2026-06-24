package com.telco.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies JwtClaimsFilter derives X-User-Id / X-User-Roles from the validated JWT and strips any
 * client-supplied identity headers (anti-spoofing). Pure unit test, no Spring context (FR-IAM-03).
 */
class JwtClaimsFilterTest {

    private final JwtClaimsFilter filter = new JwtClaimsFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectsIdentityHeadersFromAuthenticatedJwt() throws Exception {
        authenticateWith(jwt("user-123", List.of("ADMIN", "SUBSCRIBER")));

        HttpServletRequest forwarded = invokeFilter(new MockHttpServletRequest());

        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ID)).isEqualTo("user-123");
        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ROLES)).isEqualTo("ADMIN,SUBSCRIBER");
    }

    @Test
    void stripsClientSuppliedIdentityHeadersWhenUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaimsFilter.X_USER_ID, "spoofed-user");
        request.addHeader(JwtClaimsFilter.X_USER_ROLES, "ADMIN");

        HttpServletRequest forwarded = invokeFilter(request);

        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ID)).isNull();
        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ROLES)).isNull();
    }

    @Test
    void overwritesClientSuppliedHeadersWithVerifiedClaims() throws Exception {
        authenticateWith(jwt("real-user", List.of("CALL_CENTER_AGENT")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaimsFilter.X_USER_ID, "spoofed-user");
        request.addHeader(JwtClaimsFilter.X_USER_ROLES, "ADMIN");

        HttpServletRequest forwarded = invokeFilter(request);

        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ID)).isEqualTo("real-user");
        assertThat(forwarded.getHeader(JwtClaimsFilter.X_USER_ROLES)).isEqualTo("CALL_CENTER_AGENT");
    }

    private HttpServletRequest invokeFilter(MockHttpServletRequest request) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    private static void authenticateWith(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Jwt jwt(String subject, List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("roles", roles)
                .build();
    }
}
