package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.Query;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import com.telco.platform.mediator.pipeline.RequestHandlerDelegate;

import java.util.Comparator;
import java.util.List;

/**
 * Pure (no Spring) in-process {@link Mediator}. Behaviors are sorted ascending by
 * {@link PipelineBehavior#order()} so the lowest order wraps outermost; only behaviors whose
 * {@link PipelineBehavior#supports(Object)} returns true are applied, and dispatch resolves the
 * handler from the {@link HandlerRegistry}.
 */
public final class InProcessMediator implements Mediator {

    private final HandlerRegistry registry;
    private final List<PipelineBehavior> behaviors;

    public InProcessMediator(HandlerRegistry registry, List<PipelineBehavior> behaviors) {
        this.registry = registry;
        this.behaviors = behaviors.stream()
                .sorted(Comparator.comparingInt(PipelineBehavior::order))
                .toList();
    }

    @Override
    public <R> R send(Command<R> command) {
        return runThroughPipeline(command, () -> dispatchCommand(command));
    }

    @Override
    public <R> R query(Query<R> query) {
        return runThroughPipeline(query, () -> dispatchQuery(query));
    }

    @Override
    public void publish(Event event) {
        runThroughPipeline(event, () -> {
            dispatchEvent(event);
            return null;
        });
    }

    private <R> R dispatchCommand(Command<R> command) {
        CommandHandler<Command<R>, R> handler = registry.commandHandler(command.getClass());
        if (handler == null) {
            throw new IllegalStateException(
                    "No command handler registered for " + command.getClass().getName());
        }
        return handler.handle(command);
    }

    private <R> R dispatchQuery(Query<R> query) {
        QueryHandler<Query<R>, R> handler = registry.queryHandler(query.getClass());
        if (handler == null) {
            throw new IllegalStateException(
                    "No query handler registered for " + query.getClass().getName());
        }
        return handler.handle(query);
    }

    private void dispatchEvent(Event event) {
        for (EventHandler<Event> handler : registry.eventHandlers(event.getClass())) {
            handler.handle(event);
        }
    }

    private <R> R runThroughPipeline(Object request, RequestHandlerDelegate<R> terminal) {
        RequestHandlerDelegate<R> chain = terminal;
        // Iterate from highest order (innermost) to lowest so index 0 ends up outermost.
        for (int index = behaviors.size() - 1; index >= 0; index--) {
            PipelineBehavior behavior = behaviors.get(index);
            if (behavior.supports(request)) {
                RequestHandlerDelegate<R> next = chain;
                chain = () -> behavior.handle(request, next);
            }
        }
        return chain.invoke();
    }
}
