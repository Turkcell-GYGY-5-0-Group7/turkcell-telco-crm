package com.telco.platform.mediator.behavior;

import com.telco.platform.cqrs.Command;
import com.telco.platform.mediator.behavior.support.TransactionRunner;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.PipelineOrder;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;

/**
 * Wraps command handling in a transaction via {@link TransactionRunner}. Queries are not wrapped.
 */
public final class TransactionBehavior implements PipelineBehavior {

    private final TransactionRunner runner;

    public TransactionBehavior(TransactionRunner runner) {
        this.runner = runner;
    }

    @Override
    public boolean supports(Object request) {
        return request instanceof Command<?>;
    }

    @Override
    public <R> R handle(Object request, RequestHandlerDelegate<R> next) {
        return runner.executeInTransaction(next::invoke);
    }

    @Override
    public int order() {
        return PipelineOrder.TRANSACTION;
    }
}
