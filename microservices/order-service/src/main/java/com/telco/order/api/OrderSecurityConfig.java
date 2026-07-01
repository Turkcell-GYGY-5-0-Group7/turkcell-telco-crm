package com.telco.order.api;

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
class OrderSecurityConfig {

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
                                // Trusted system-to-system order read for the onboarding saga. The
                                // gateway excludes /internal/** from public traffic (devops).
                                "/internal/**")
                        .permitAll()
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
