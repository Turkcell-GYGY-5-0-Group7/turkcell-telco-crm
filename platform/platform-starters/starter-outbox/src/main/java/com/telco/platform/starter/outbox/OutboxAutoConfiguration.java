package com.telco.platform.starter.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.outbox.DefaultOutboxService;
import com.telco.platform.outbox.EventSerializer;
import com.telco.platform.outbox.OutboxService;
import com.telco.platform.outbox.OutboxStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the transactional outbox write-side onto JDBC (ADR-005, ADR-009).
 *
 * <p>Active when {@code telco.platform.outbox.enabled} is not {@code false} and a
 * {@link JdbcTemplate} is available. The optional polling relay is enabled separately via
 * {@code telco.platform.outbox.relay.enabled} (Debezium delivers by default).
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "telco.platform.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventSerializer outboxEventSerializer(ObjectMapper objectMapper) {
        return new JacksonEventSerializer(objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(OutboxStore.class)
    public JdbcOutboxStore outboxStore(JdbcTemplate jdbcTemplate, OutboxProperties properties) {
        return new JdbcOutboxStore(jdbcTemplate, properties.getTable());
    }

    @Bean
    @ConditionalOnBean(OutboxStore.class)
    @ConditionalOnMissingBean(OutboxService.class)
    public OutboxService outboxService(OutboxStore store, EventSerializer serializer) {
        return new DefaultOutboxService(store, serializer);
    }

    /** Optional relay; off by default since Debezium CDC is the primary delivery (ADR-005). */
    @AutoConfiguration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "telco.platform.outbox.relay", name = "enabled", havingValue = "true")
    public static class OutboxRelayConfiguration {

        @Bean
        @ConditionalOnBean(OutboxStore.class)
        @ConditionalOnMissingBean
        public OutboxRelayScheduler outboxRelayScheduler(OutboxStore store, OutboxProperties properties) {
            return new OutboxRelayScheduler(store, properties.getRelay().getBatchSize());
        }
    }

    /** Stuck-row sweeper; on by default (read-only monitoring of NEW rows Debezium has not taken). */
    @AutoConfiguration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "telco.platform.outbox.sweeper", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public static class OutboxSweeperConfiguration {

        @Bean
        @ConditionalOnBean(OutboxStore.class)
        @ConditionalOnMissingBean
        public OutboxStuckSweeper outboxStuckSweeper(OutboxStore store, OutboxProperties properties) {
            return new OutboxStuckSweeper(store, java.time.Duration.ofMillis(properties.getSweeper().getStaleThresholdMs()));
        }
    }
}
