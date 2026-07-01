package com.telco.billing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

    @Lazy
    @Bean
    RestClient productCatalogRestClient(
            @Value("${telco.clients.product-catalog-service.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
