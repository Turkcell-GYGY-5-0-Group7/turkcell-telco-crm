package com.telco.platform.starter.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.Query;
import com.telco.platform.cqrs.QueryHandler;
import com.telco.platform.mediator.HandlerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring-backed {@link HandlerRegistry} that indexes {@link CommandHandler}, {@link QueryHandler}
 * and {@link EventHandler} beans from the application context by the request type they handle.
 *
 * <p>The index is built lazily on first use to avoid forcing eager handler instantiation during
 * context refresh; command/query types resolve to a single handler while event types may have many.
 */
public final class SpringHandlerRegistry implements HandlerRegistry {

    private final ObjectProvider<CommandHandler<?, ?>> commandHandlers;
    private final ObjectProvider<QueryHandler<?, ?>> queryHandlers;
    private final ObjectProvider<EventHandler<?>> eventHandlers;

    private volatile Map<Class<?>, CommandHandler<?, ?>> commandIndex;
    private volatile Map<Class<?>, QueryHandler<?, ?>> queryIndex;
    private volatile Map<Class<?>, List<EventHandler<?>>> eventIndex;

    public SpringHandlerRegistry(ObjectProvider<CommandHandler<?, ?>> commandHandlers,
                                 ObjectProvider<QueryHandler<?, ?>> queryHandlers,
                                 ObjectProvider<EventHandler<?>> eventHandlers) {
        this.commandHandlers = commandHandlers;
        this.queryHandlers = queryHandlers;
        this.eventHandlers = eventHandlers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CommandHandler<Command<R>, R> commandHandler(Class<?> commandType) {
        return (CommandHandler<Command<R>, R>) commandIndex().get(commandType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> QueryHandler<Query<R>, R> queryHandler(Class<?> queryType) {
        return (QueryHandler<Query<R>, R>) queryIndex().get(queryType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EventHandler<Event>> eventHandlers(Class<?> eventType) {
        List<EventHandler<?>> handlers = eventIndex().get(eventType);
        if (handlers == null) {
            return List.of();
        }
        return (List<EventHandler<Event>>) (List<?>) handlers;
    }

    private Map<Class<?>, CommandHandler<?, ?>> commandIndex() {
        Map<Class<?>, CommandHandler<?, ?>> local = commandIndex;
        if (local == null) {
            synchronized (this) {
                local = commandIndex;
                if (local == null) {
                    local = buildSingleIndex(commandHandlers, CommandHandler.class, "command");
                    commandIndex = local;
                }
            }
        }
        return local;
    }

    private Map<Class<?>, QueryHandler<?, ?>> queryIndex() {
        Map<Class<?>, QueryHandler<?, ?>> local = queryIndex;
        if (local == null) {
            synchronized (this) {
                local = queryIndex;
                if (local == null) {
                    local = buildSingleIndex(queryHandlers, QueryHandler.class, "query");
                    queryIndex = local;
                }
            }
        }
        return local;
    }

    private Map<Class<?>, List<EventHandler<?>>> eventIndex() {
        Map<Class<?>, List<EventHandler<?>>> local = eventIndex;
        if (local == null) {
            synchronized (this) {
                local = eventIndex;
                if (local == null) {
                    local = buildEventIndex();
                    eventIndex = local;
                }
            }
        }
        return local;
    }

    private static <H> Map<Class<?>, H> buildSingleIndex(ObjectProvider<H> provider,
                                                         Class<?> handlerInterface,
                                                         String kind) {
        Map<Class<?>, H> index = new HashMap<>();
        provider.orderedStream().forEach(handler -> {
            Class<?> requestType = resolveTypeArgument(handler, handlerInterface, 0);
            if (requestType == null) {
                return;
            }
            H existing = index.putIfAbsent(requestType, handler);
            if (existing != null) {
                throw new IllegalStateException("Multiple " + kind + " handlers registered for "
                        + requestType.getName() + ": " + existing.getClass().getName()
                        + " and " + handler.getClass().getName());
            }
        });
        return index;
    }

    private Map<Class<?>, List<EventHandler<?>>> buildEventIndex() {
        Map<Class<?>, List<EventHandler<?>>> index = new HashMap<>();
        eventHandlers.orderedStream().forEach(handler -> {
            Class<?> eventType = resolveTypeArgument(handler, EventHandler.class, 0);
            if (eventType == null) {
                return;
            }
            index.computeIfAbsent(eventType, key -> new ArrayList<>()).add(handler);
        });
        return index;
    }

    private static Class<?> resolveTypeArgument(Object handler, Class<?> handlerInterface, int argIndex) {
        ResolvableType type = ResolvableType.forClass(handler.getClass()).as(handlerInterface);
        Class<?> resolved = type.getGeneric(argIndex).resolve();
        return resolved;
    }
}
