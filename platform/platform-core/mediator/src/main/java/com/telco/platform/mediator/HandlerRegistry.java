package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.EventHandler;
import com.telco.platform.cqrs.Query;
import com.telco.platform.cqrs.QueryHandler;

import java.util.List;

/**
 * Resolves handlers by request type. The Spring-backed implementation lives in starter-mediator.
 */
public interface HandlerRegistry {

    /** Returns the command handler for the given command type, or null if none. */
    <R> CommandHandler<Command<R>, R> commandHandler(Class<?> commandType);

    /** Returns the query handler for the given query type, or null if none. */
    <R> QueryHandler<Query<R>, R> queryHandler(Class<?> queryType);

    /** Returns all event handlers for the given event type; never null. */
    List<EventHandler<Event>> eventHandlers(Class<?> eventType);
}
