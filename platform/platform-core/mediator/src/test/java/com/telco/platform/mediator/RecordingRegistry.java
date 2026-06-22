package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.Query;
import com.telco.platform.cqrs.QueryHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal in-memory {@link HandlerRegistry} for tests. */
final class RecordingRegistry implements HandlerRegistry {

    private final Map<Class<?>, CommandHandler<?, ?>> commands = new HashMap<>();
    private final Map<Class<?>, QueryHandler<?, ?>> queries = new HashMap<>();
    private final Map<Class<?>, List<EventHandler<Event>>> events = new HashMap<>();

    void registerCommand(Class<?> type, CommandHandler<?, ?> handler) {
        commands.put(type, handler);
    }

    void registerQuery(Class<?> type, QueryHandler<?, ?> handler) {
        queries.put(type, handler);
    }

    void registerEvents(Class<?> type, List<EventHandler<Event>> handlers) {
        events.put(type, handlers);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CommandHandler<Command<R>, R> commandHandler(Class<?> commandType) {
        return (CommandHandler<Command<R>, R>) commands.get(commandType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> QueryHandler<Query<R>, R> queryHandler(Class<?> queryType) {
        return (QueryHandler<Query<R>, R>) queries.get(queryType);
    }

    @Override
    public List<EventHandler<Event>> eventHandlers(Class<?> eventType) {
        return events.getOrDefault(eventType, List.of());
    }
}
