package com.telco.platform.starter.mediator;

import com.telco.platform.mediator.behavior.support.TransactionRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Adapts the framework-agnostic {@link TransactionRunner} port to Spring's {@link TransactionTemplate}.
 *
 * <p>Used by {@code TransactionBehavior} so command handlers run inside a managed transaction,
 * which is what makes the transactional outbox guarantee hold (the business write and the outbox
 * append commit together).
 */
public final class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <R> R executeInTransaction(Supplier<R> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
