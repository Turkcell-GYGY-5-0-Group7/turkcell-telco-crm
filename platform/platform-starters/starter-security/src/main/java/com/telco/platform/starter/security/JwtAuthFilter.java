package com.telco.platform.starter.security;

import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Establishes the authenticated principal for each request (ADR-011).
 *
 * <p>Two trust models: when gateway-trust is enabled, the filter trusts {@code X-User-Id} /
 * {@code X-User-Roles} headers injected by the API gateway (gateway-behind-trust); otherwise it
 * validates the {@code Authorization: Bearer} JWT via {@link JwtService}. On success it populates
 * {@link UserContextHolder} and the Spring {@code SecurityContext}. Missing/invalid credentials are
 * not rejected here - that is left to Spring Security - and the user context is always cleared at
 * the end of the request.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final JwtProperties properties;

    public JwtAuthFilter(JwtService jwtService, JwtProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            UserContext user = properties.getGatewayTrust().isEnabled()
                    ? fromGatewayHeaders(request)
                    : fromBearerToken(request);
            if (user != null) {
                UserContextHolder.set(user);
                authenticate(user);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private UserContext fromGatewayHeaders(HttpServletRequest request) {
        String userId = request.getHeader(properties.getGatewayTrust().getUserIdHeader());
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        String rolesHeader = request.getHeader(properties.getGatewayTrust().getRolesHeader());
        Set<String> roles = parseRoles(rolesHeader);
        return new UserContext(userId, roles, null);
    }

    private UserContext fromBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parse(token);
            return new UserContext(claims.getSubject(), jwtService.roles(claims), null);
        } catch (RuntimeException ex) {
            LOGGER.debug("Rejecting invalid JWT: {}", ex.getMessage());
            return null;
        }
    }

    private void authenticate(UserContext user) {
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role))
                .collect(Collectors.toList());
        var authentication = new UsernamePasswordAuthenticationToken(user.userId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Set<String> parseRoles(String header) {
        if (!StringUtils.hasText(header)) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
