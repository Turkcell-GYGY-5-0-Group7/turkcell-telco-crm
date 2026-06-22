package com.telco.platform.starter.api;

import com.telco.platform.common.logging.ExceptionLogWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Registers the platform {@link GlobalExceptionHandler} so every service returns the standard
 * {@link com.telco.platform.common.api.ApiResult} error contract (ADR-015).
 *
 * <p>Active only on servlet web applications and when {@code telco.platform.api.enabled} is not
 * {@code false}. Services may supply their own {@code @RestControllerAdvice} to override.
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnProperty(prefix = "telco.platform.api", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiProperties.class)
public class ApiExceptionHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ApiMetaFactory apiMetaFactory(Environment environment) {
        return new ApiMetaFactory(environment.getProperty("spring.application.name", "unknown"));
    }

    @Bean
    @ConditionalOnMissingBean
    ExceptionLogRecorder exceptionLogRecorder(Environment environment,
                                              ObjectProvider<ExceptionLogWriter> exceptionLogWriters) {
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        return new ExceptionLogRecorder(serviceName, exceptionLogWriters.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiResponseFactory apiResponseFactory(ApiMetaFactory apiMetaFactory) {
        return new ApiResponseFactory(apiMetaFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler platformGlobalExceptionHandler(ApiMetaFactory apiMetaFactory,
                                                                 ExceptionLogRecorder exceptionLogRecorder) {
        return new GlobalExceptionHandler(apiMetaFactory, exceptionLogRecorder);
    }
}
