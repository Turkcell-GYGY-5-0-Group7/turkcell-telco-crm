package com.telco.platform.starter.mediator;

import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.platform.mediator.HandlerRegistry;
import com.telco.platform.mediator.InProcessMediator;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.mediator.behavior.AuthorizationBehavior;
import com.telco.platform.mediator.behavior.LoggingBehavior;
import com.telco.platform.mediator.behavior.PerformanceBehavior;
import com.telco.platform.mediator.behavior.Slf4jRequestLogWriter;
import com.telco.platform.mediator.behavior.TransactionBehavior;
import com.telco.platform.mediator.behavior.ValidationBehavior;
import com.telco.platform.mediator.behavior.support.AuthorizationRule;
import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Wires the in-process mediator and its pipeline behaviors (ADR-008).
 *
 * <p>Always-on behaviors: Performance, Logging, Authorization. Conditional behaviors: Validation
 * (requires a {@link Validator} bean) and Transaction (requires a {@link PlatformTransactionManager}).
 * The Inbox behavior, when present, is contributed by {@code starter-inbox} as a separate
 * {@link PipelineBehavior} bean and picked up automatically.
 *
 * <p>Ordered AFTER the auto-configurations that create the transaction manager and validator so the
 * {@link ConditionalOnBean} checks see those beans; otherwise the transactional behavior would be
 * silently skipped, breaking the outbox guarantee. {@code afterName} keeps this starter decoupled
 * from those classes.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration"
})
@EnableConfigurationProperties(MediatorProperties.class)
public class MediatorAutoConfiguration {

    /** Spring-backed registry resolving handler beans by the request type they handle. */
    @Bean
    @ConditionalOnMissingBean(HandlerRegistry.class)
    public SpringHandlerRegistry platformHandlerRegistry(ObjectProvider<CommandHandler<?, ?>> commandHandlers,
                                                         ObjectProvider<QueryHandler<?, ?>> queryHandlers,
                                                         ObjectProvider<EventHandler<?>> eventHandlers) {
        return new SpringHandlerRegistry(commandHandlers, queryHandlers, eventHandlers);
    }

    /** The single mediator entry point; applies the registered behaviors in order. */
    @Bean
    @ConditionalOnMissingBean(Mediator.class)
    public InProcessMediator mediator(HandlerRegistry registry, List<PipelineBehavior> behaviors) {
        return new InProcessMediator(registry, behaviors);
    }

    /** Default user provider; overridden by starter-security when present. */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider defaultCurrentUserProvider() {
        return new CurrentUserProvider() {
        };
    }

    /** Default request-log sink writing through SLF4J. */
    @Bean
    @ConditionalOnMissingBean(RequestLogWriter.class)
    public Slf4jRequestLogWriter slf4jRequestLogWriter() {
        return new Slf4jRequestLogWriter();
    }

    @Bean
    @ConditionalOnMissingBean
    public PerformanceBehavior performanceBehavior(MediatorProperties properties) {
        return new PerformanceBehavior(properties.getPerformance().getSlowThresholdMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingBehavior loggingBehavior(Environment environment, List<RequestLogWriter> writers) {
        String serviceName = environment.getProperty("spring.application.name", "unknown");
        return new LoggingBehavior(serviceName, writers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationBehavior authorizationBehavior(CurrentUserProvider currentUserProvider,
                                                       List<AuthorizationRule> rules) {
        return new AuthorizationBehavior(currentUserProvider, rules);
    }

    @Bean
    @ConditionalOnBean(Validator.class)
    @ConditionalOnMissingBean
    public ValidationBehavior validationBehavior(Validator validator) {
        return new ValidationBehavior(validator);
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    @ConditionalOnMissingBean
    public TransactionBehavior transactionBehavior(PlatformTransactionManager transactionManager) {
        SpringTransactionRunner runner = new SpringTransactionRunner(new TransactionTemplate(transactionManager));
        return new TransactionBehavior(runner);
    }
}
