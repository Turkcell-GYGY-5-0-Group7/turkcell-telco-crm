package com.telco.platform.starter.security;

import com.telco.platform.common.context.CurrentUserProvider;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configures platform security (ADR-011).
 *
 * <p>Registers a {@link JwtAuthFilter} that either validates a Bearer JWT or trusts gateway-injected
 * identity headers, and a {@link CurrentUserProvider} that reads the resulting principal. Ordered
 * before the mediator auto-configuration so this {@code CurrentUserProvider} wins over the mediator's
 * anonymous default. Active when {@code telco.platform.security.enabled} is not {@code false} and the
 * security/JWT classes are present.
 */
@AutoConfiguration(beforeName = "com.telco.platform.starter.mediator.MediatorAutoConfiguration")
@ConditionalOnClass({Jwts.class, Filter.class})
@ConditionalOnProperty(prefix = "telco.platform.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfiguration {

    /**
     * JWT validation/issuing service. Created only when gateway-trust is disabled (Bearer mode),
     * since gateway-trust deployments authenticate via headers and need no signing key.
     */
    @Bean
    @ConditionalOnProperty(prefix = "telco.platform.security", name = "gateway-trust.enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties.getJwt());
    }

    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider currentUserProvider() {
        return new UserContextCurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthFilter jwtAuthFilter(ObjectProvider<JwtService> jwtService, JwtProperties properties) {
        return new JwtAuthFilter(jwtService.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformJwtAuthFilterRegistration")
    public FilterRegistrationBean<JwtAuthFilter> platformJwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
