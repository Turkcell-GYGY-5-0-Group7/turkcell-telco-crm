package com.telco.gateway.config;

import io.lettuce.core.resource.ClientResources;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Overrides Lettuce's default Netty async DNS resolver with the JVM resolver
 * (io.netty.resolver.DefaultAddressResolverGroup) so that Redis connections
 * work correctly on macOS without netty-resolver-dns-native-macos on the classpath.
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        return ClientResources.builder()
                .addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)
                .build();
    }
}
