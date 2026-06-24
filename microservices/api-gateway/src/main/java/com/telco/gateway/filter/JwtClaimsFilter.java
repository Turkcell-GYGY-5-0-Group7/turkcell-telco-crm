package com.telco.gateway.filter;

import com.telco.gateway.support.MutableHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Runs after the Spring Security filter chain has validated the JWT. Strips any
 * client-supplied identity headers (anti-spoofing, FR-IAM-03), then injects
 * X-User-Id and X-User-Roles derived from the verified token so downstream
 * services can trust them (gateway-behind-trust, ADR-011).
 */
@Component
@Order(10)
public class JwtClaimsFilter extends OncePerRequestFilter {

    static final String X_USER_ID = "X-User-Id";
    static final String X_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);

        // Strip any client-supplied identity headers unconditionally.
        mutable.removeHeader(X_USER_ID);
        mutable.removeHeader(X_USER_ROLES);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getSubject();
            if (userId != null && !userId.isBlank()) {
                mutable.putHeader(X_USER_ID, userId);
            }

            // Keycloak maps realm roles to a flat "roles" claim via the telco-roles scope.
            Object rolesClaim = jwtAuth.getToken().getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                String rolesHeader = roles.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
                mutable.putHeader(X_USER_ROLES, rolesHeader);
            }
        }

        chain.doFilter(mutable, response);
    }
}
