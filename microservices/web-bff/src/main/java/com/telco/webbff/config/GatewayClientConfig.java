package com.telco.webbff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures the single outbound {@link RestClient} web-bff uses to reach the API gateway
 * (ADR-005, ADR-022). The gateway base URL is sourced from config-server
 * ({@code telco.gateway.base-url}) and is never hardcoded. Every request carries the caller's bearer
 * token via {@link BearerTokenRelayInterceptor}. web-bff calls only the gateway ({@code /api/v1/**});
 * it never calls a domain-service port directly (ADR-011 Section 2, ADR-022).
 */
@Configuration
public class GatewayClientConfig {

    @Bean
    public RestClient gatewayRestClient(@Value("${telco.gateway.base-url}") String gatewayBaseUrl,
                                        BearerTokenRelayInterceptor bearerTokenRelayInterceptor) {
        return RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestInterceptor(bearerTokenRelayInterceptor)
                .build();
    }
}
