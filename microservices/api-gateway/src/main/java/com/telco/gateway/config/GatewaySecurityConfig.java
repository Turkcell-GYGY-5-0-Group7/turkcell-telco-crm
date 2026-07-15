package com.telco.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * Gateway security: validates Keycloak-issued JWTs against the realm JWKS (ADR-011, FR-IAM-02).
 * Allowlisted paths (Keycloak OIDC, actuator health, Swagger) pass without a token.
 * Returns ApiResult-shaped JSON on 401/403 so clients get a consistent error envelope.
 */
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwks-uri}")
    private String jwksUri;

    @Value("${gateway.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> corsAllowedOrigins;

    private final ObjectMapper objectMapper;

    public GatewaySecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Explicit JwtDecoder so Spring Security does not rely on auto-configuration ordering.
     * NimbusJwtDecoder fetches JWKS lazily on first token validation, not at startup.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // /internal/** is a service-to-service-only surface (ADR-011) that the gateway
                // never proxies downstream - the internal-deny-route forwards it to a local 404
                // sink (GatewayRouteConfig#internalDenyRouterFunction, /__gateway_blocked).
                // PermitAll so the deny returns a clean 404 (path absent at the edge) rather than a
                // misleading 401; the request still never reaches any downstream /internal endpoint.
                .requestMatchers("/internal/**", "/__gateway_blocked").permitAll()
                // Keycloak OIDC endpoints proxied through the gateway.
                .requestMatchers("/realms/**").permitAll()
                // Actuator (liveness/readiness probes and info); no auth needed.
                .requestMatchers("/actuator/**").permitAll()
                // Swagger / OpenAPI (gateway aggregation + per-service specs).
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs/**", "/api-docs/**").permitAll()
                // Error page must be permitted so filter exceptions don't cascade to 401.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthConverter()))
                .authenticationEntryPoint(unauthorizedHandler())
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedHandler())
                .accessDeniedHandler(forbiddenHandler())
            );

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        // Keycloak exposes realm roles as a flat "roles" claim via the telco-roles scope mapper.
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Wildcard origin + allowCredentials=true allows any site to make credentialed
        // cross-origin requests — this is the classic CORS credential-leak misconfiguration.
        // Explicit allowlist is mandatory; configure CORS_ALLOWED_ORIGINS in production.
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Idempotency-Key is a non-safelisted request header the web channel sends on POST writes
        // (order + payment); it must be allowlisted or the cross-origin preflight blocks those flows.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id",
                "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint unauthorizedHandler() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success", false,
                "error", Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Authentication required"
                )
            )));
        };
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (req, res, ex) -> {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success", false,
                "error", Map.of(
                    "code", "FORBIDDEN",
                    "message", "Access denied"
                )
            )));
        };
    }
}
