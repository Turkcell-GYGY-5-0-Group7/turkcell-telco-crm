package com.telco.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Route definitions are declared in the config-server YAML
 * (microservices/configs/api-gateway/application.yml, 4.3.1).
 * The GatewayMvcPropertiesBeanDefinitionRegistrar registers them automatically
 * via spring.cloud.gateway.server.webmvc.routes.
 */
@Configuration
public class GatewayRouteConfig {
    // Routes are in YAML, not Java, to keep them centrally configurable.
}
