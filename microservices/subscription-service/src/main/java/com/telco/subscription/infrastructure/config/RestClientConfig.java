package com.telco.subscription.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires the single downstream HTTP dependency of subscription-service: order-service. The base URL is
 * driven by the {@code telco.clients.order.url} property (configurable per environment). The
 * Resilience4j {@link CircuitBreaker} is hand-rolled here until {@code starter-resilience} ships
 * (platform-capabilities.md "Planned"), mirroring order-service's setup.
 */
@Configuration
public class RestClientConfig {

    private static final CircuitBreakerConfig ORDER_CB_CONFIG = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

    private static final CircuitBreakerRegistry REGISTRY =
            CircuitBreakerRegistry.of(ORDER_CB_CONFIG);

    @Lazy
    @Bean
    public RestClient orderRestClient(@Value("${telco.clients.order.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public CircuitBreaker orderServiceCircuitBreaker() {
        return REGISTRY.circuitBreaker("order-service");
    }
}
