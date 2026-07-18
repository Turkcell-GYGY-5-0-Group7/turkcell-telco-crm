package com.telco.fraud.infrastructure.config;

import com.telco.platform.starter.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Wires the platform JWT filter into a stateless security chain (ADR-011), mirroring
 * campaign-service's {@code CampaignSecurityConfig} and ticket-service's security wiring. The
 * fraud-case and rule-config API ({@code /api/v1/fraud-cases/**}, {@code /api/v1/fraud-rules/**},
 * Feature 23.3) requires a JWT like every other
 * CQRS + Mediator domain service; {@code /internal/**} (reserved for any future trusted
 * system-to-system surface) stays tokenless behind the gateway's {@code internal-deny-route} (a
 * {@code permitAll} route under {@code /api/v1/**} would still be internet-reachable if called
 * directly, so the tokenless surface must live under {@code /internal/**}, never {@code /api/v1}).
 *
 * <p>No business endpoints exist yet in the Feature 23.1 scaffold; this chain simply ensures the
 * actuator health/info probes are open and everything else authenticates once the API lands (23.3).
 */
@Configuration
@EnableMethodSecurity
public class FraudSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/actuator/health", "/actuator/info",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/internal/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )
                .build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> platformJwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(jwtAuthFilter);
        bean.setEnabled(false);
        return bean;
    }
}
