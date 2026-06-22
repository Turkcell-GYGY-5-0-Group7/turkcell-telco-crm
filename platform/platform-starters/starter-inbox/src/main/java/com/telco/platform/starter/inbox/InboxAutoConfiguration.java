package com.telco.platform.starter.inbox;

import com.telco.platform.inbox.DefaultInboxService;
import com.telco.platform.inbox.InboxBehavior;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.inbox.InboxStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires inbox idempotency onto JDBC and contributes the mediator inbox behavior (ADR-005).
 *
 * <p>The {@link InboxBehavior} is registered as a {@code PipelineBehavior} bean so starter-mediator
 * picks it up automatically; requests implementing {@code IdempotentRequest} are then deduplicated.
 * Active when {@code telco.platform.inbox.enabled} is not {@code false} and a {@link JdbcTemplate}
 * is available.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "telco.platform.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InboxProperties.class)
public class InboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(InboxStore.class)
    public JdbcInboxStore inboxStore(JdbcTemplate jdbcTemplate, InboxProperties properties) {
        return new JdbcInboxStore(jdbcTemplate, properties.getTable());
    }

    @Bean
    @ConditionalOnBean(InboxStore.class)
    @ConditionalOnMissingBean(InboxService.class)
    public InboxService inboxService(InboxStore store) {
        return new DefaultInboxService(store);
    }

    @Bean
    @ConditionalOnBean(InboxService.class)
    @ConditionalOnMissingBean(InboxBehavior.class)
    public InboxBehavior inboxBehavior(InboxService inboxService) {
        return new InboxBehavior(inboxService);
    }
}
