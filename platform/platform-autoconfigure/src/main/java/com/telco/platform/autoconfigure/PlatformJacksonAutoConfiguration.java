package com.telco.platform.autoconfigure;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Shared Jackson configuration for platform response contracts.
 *
 * <p>Registers the JSR-310 module so {@code java.time} types (for example {@code ApiMeta.timestamp})
 * serialize as ISO-8601 strings rather than numeric timestamps (ADR-015). Inclusion shaping
 * ({@code NON_NULL}) is declared per type via {@code @JsonInclude} in {@code platform-common};
 * this keeps the autoconfigure module minimal as required by ADR-020.
 */
@AutoConfiguration
@ConditionalOnClass(JavaTimeModule.class)
public class PlatformJacksonAutoConfiguration {

    /**
     * Contributes the JSR-310 module. Spring Boot detects {@code com.fasterxml.jackson.databind.Module}
     * beans and registers them with the application {@code ObjectMapper}.
     */
    @Bean
    @ConditionalOnMissingBean
    public JavaTimeModule platformJavaTimeModule() {
        return new JavaTimeModule();
    }
}
