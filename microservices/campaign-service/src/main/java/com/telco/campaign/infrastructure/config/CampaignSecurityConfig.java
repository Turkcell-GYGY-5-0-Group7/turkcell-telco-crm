package com.telco.campaign.infrastructure.config;

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
 * Wires the platform JWT filter into a stateless security chain (ADR-011). campaign-service's admin
 * API (`/api/v1/campaigns/**`) requires a JWT like every other CQRS + Mediator domain service; the
 * validate endpoint (`/internal/campaigns/validate`, Feature 21.3.1) is tokenless,
 * network-perimeter-trust instead - order-service is the only caller (ADR-027 Decision Section 4,
 * no gateway route per feature 21.1.3), mirroring `product-catalog-service`'s
 * `CatalogSecurityConfig`/`TariffInternalController` verbatim (tech-lead ruling 2026-07-06, extended
 * to campaign-service by the 2026-07-13 ADR-027 second ratification addendum). Only
 * {@code /internal/**} is excluded from public traffic at the gateway
 * ({@code internal-deny-route} -> 404); a {@code permitAll} route under {@code /api/v1/**} would still
 * be internet-reachable if called directly, so the tokenless surface must live under
 * {@code /internal/**}, never {@code /api/v1}.
 */
@Configuration
@EnableMethodSecurity
public class CampaignSecurityConfig {

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
                                // Trusted system-to-system campaign validation (Feature 21.3.1).
                                // Moved off /api/v1 deliberately: only /internal/** is firewalled at
                                // the gateway (internal-deny-route -> 404); the gateway excludes it
                                // from public traffic (devops).
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
