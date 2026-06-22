package com.telco.platform.starter.logpersistence;

import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Opt-in JDBC persistence of request and exception logs for local/test environments (ADR-012 Loki
 * remains the production default). Enabled with {@code telco.platform.logging.persistence.enabled=true}
 * and requires a {@link JdbcTemplate}. Tables ship as Flyway migration V902.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "telco.platform.logging.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LogPersistenceProperties.class)
public class LogPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public JdbcExceptionLogWriter jdbcExceptionLogWriter(JdbcTemplate jdbcTemplate,
                                                         LogPersistenceProperties properties) {
        return new JdbcExceptionLogWriter(jdbcTemplate, properties.getExceptionTable());
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public JdbcRequestLogWriter jdbcRequestLogWriter(JdbcTemplate jdbcTemplate,
                                                     LogPersistenceProperties properties) {
        return new JdbcRequestLogWriter(jdbcTemplate, properties.getRequestTable());
    }

    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnProperty(prefix = "telco.platform.logging.persistence", name = "http-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public HttpRequestLogFilter httpRequestLogFilter(Environment environment,
                                                     ObjectProvider<RequestLogWriter> writers) {
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        return new HttpRequestLogFilter(serviceName, writers.orderedStream().toList());
    }
}
