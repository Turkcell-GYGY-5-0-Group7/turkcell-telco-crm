package com.telco.order.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Creates {@link RestClient} instances for each downstream service dependency.
 * Base URLs are driven by {@code telco.clients.*} properties (configurable per environment).
 *
 * <p>Every client is built with a bounded connect/read timeout. Without a read timeout a slow
 * downstream makes the call slow-but-successful, so the Resilience4j circuit breakers registered
 * in {@code ResilienceConfig} never see a failure to count and never open - the call just piles up
 * on the calling thread. A finite read timeout turns a hung/slow downstream into a counted failure
 * (SocketTimeoutException), which is what lets the failure-rate breaker path actually trip.
 */
@Configuration
public class RestClientConfig {

    // Bounded timeouts so a hung or slow downstream fails fast instead of blocking the calling
    // thread indefinitely, and the failure is surfaced to the Resilience4j circuit breakers.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Lazy
    @Bean
    public RestClient customerRestClient(
            @Value("${telco.clients.customer-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Lazy
    @Bean
    public RestClient productCatalogRestClient(
            @Value("${telco.clients.product-catalog-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Lazy
    @Bean
    public RestClient campaignRestClient(
            @Value("${telco.clients.campaign-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }
}
