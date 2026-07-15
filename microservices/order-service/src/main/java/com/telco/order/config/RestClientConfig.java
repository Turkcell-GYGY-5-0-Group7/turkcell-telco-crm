package com.telco.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/**
 * Creates {@link RestClient} instances for each downstream service dependency.
 * Base URLs are driven by {@code telco.clients.*} properties (configurable per environment).
 */
@Configuration
public class RestClientConfig {

    @Lazy
    @Bean
    public RestClient customerRestClient(
            @Value("${telco.clients.customer-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Lazy
    @Bean
    public RestClient productCatalogRestClient(
            @Value("${telco.clients.product-catalog-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Lazy
    @Bean
    public RestClient campaignRestClient(
            @Value("${telco.clients.campaign-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
