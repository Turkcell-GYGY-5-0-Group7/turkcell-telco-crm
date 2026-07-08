package com.telco.catalog.api;

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

@Configuration
@EnableMethodSecurity
class CatalogSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
            throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/actuator/health", "/actuator/info",
                                "/swagger-ui/**", "/v3/api-docs/**",
                                // Trusted system-to-system tariff reads (by-id, price-snapshot,
                                // allowance-snapshot). Moved off the public /api/v1 surface
                                // (tech-lead ruling 2026-07-06, tariff endpoint internal-surface
                                // fix): only /internal/** is actually firewalled at the gateway
                                // (internal-deny-route -> 404), so permitAll routes under
                                // /api/v1/** were genuinely internet-reachable without a JWT. The
                                // gateway excludes /internal/** from public traffic (devops).
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

    /**
     * Disables the platform's servlet-level registration of JwtAuthFilter. The filter is added
     * directly to the SecurityFilterChain above so it runs inside Spring Security's context
     * management lifecycle rather than before it as a plain servlet filter.
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> platformJwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(jwtAuthFilter);
        bean.setEnabled(false);
        return bean;
    }
}
