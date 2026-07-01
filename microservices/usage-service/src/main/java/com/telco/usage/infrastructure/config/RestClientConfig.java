package com.telco.usage.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/** Creates the {@link RestClient} instances for downstream service dependencies. */
@Configuration
public class RestClientConfig {

    @Lazy
    @Bean
    public RestClient productCatalogRestClient(
            @Value("${telco.clients.product-catalog-service.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
