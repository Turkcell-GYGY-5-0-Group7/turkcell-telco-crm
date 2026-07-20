package com.telco.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Hand-rolled until starter-resilience is available (platform-capabilities.md "Planned").
@Configuration
public class ResilienceConfig {

    private static final CircuitBreakerConfig DEFAULT_CONFIG = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

    private static final CircuitBreakerRegistry REGISTRY =
            CircuitBreakerRegistry.of(DEFAULT_CONFIG);

    @Bean
    public CircuitBreaker customerServiceCircuitBreaker() {
        return REGISTRY.circuitBreaker("customer-service");
    }

    @Bean
    public CircuitBreaker productCatalogCircuitBreaker() {
        return REGISTRY.circuitBreaker("product-catalog-service");
    }

    /**
     * Same default config shape as the other two breakers (ADR-027 Decision Section 4 acceptance
     * criteria for Feature 21.3.2) - no documented reason yet justifies different tuning for a
     * fail-open dependency. {@code CampaignServiceClient} is itself fail-open (catches
     * {@code CallNotPermittedException} and returns a "no discount" sentinel), so an OPEN breaker
     * here degrades order pricing (no discount applied), never order availability.
     */
    @Bean
    public CircuitBreaker campaignServiceCircuitBreaker() {
        return REGISTRY.circuitBreaker("campaign-service");
    }
}
