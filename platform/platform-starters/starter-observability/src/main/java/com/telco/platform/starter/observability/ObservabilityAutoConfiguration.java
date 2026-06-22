package com.telco.platform.starter.observability;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configures correlation propagation for the platform (ADR-012, ADR-015).
 *
 * <p>Registers the {@link CorrelationFilter} at the front of the servlet chain so every request and
 * log line carries a traceId and correlationId. Active on servlet web applications when
 * {@code telco.platform.observability.correlation.enabled} is not {@code false}. Tracing is provided
 * by Spring Boot/Micrometer when those libraries are present; this starter only ensures the
 * correlation context exists regardless.
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "telco.platform.observability.correlation", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CorrelationFilter> correlationFilterRegistration(ObservabilityProperties properties) {
        CorrelationFilter filter = new CorrelationFilter(properties.getCorrelation().getHeader());
        FilterRegistrationBean<CorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
