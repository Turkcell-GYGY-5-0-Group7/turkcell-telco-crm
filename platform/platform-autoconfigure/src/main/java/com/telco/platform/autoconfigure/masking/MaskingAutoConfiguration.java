package com.telco.platform.autoconfigure.masking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the masking {@code ObjectMapper} for the log/persistence view (ADR-021, Layer A).
 *
 * <p>Active when {@code telco.platform.logging.masking.enabled} is not {@code false}. The bean is
 * named {@code maskingObjectMapper} and is intentionally not {@code @Primary}: log writers inject it
 * by qualifier, while the application {@code ObjectMapper} (used by events and HTTP responses)
 * remains unmasked.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(MaskingProperties.class)
@ConditionalOnProperty(prefix = "telco.platform.logging.masking", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MaskingAutoConfiguration {

    /** Name of the masking {@code ObjectMapper} bean for qualifier-based injection. */
    public static final String MASKING_OBJECT_MAPPER = "maskingObjectMapper";

    @Bean(MASKING_OBJECT_MAPPER)
    public ObjectMapper maskingObjectMapper(MaskingProperties properties) {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new SensitiveMaskingModule(properties.getMaskChar(), properties.getKeepLast()))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
