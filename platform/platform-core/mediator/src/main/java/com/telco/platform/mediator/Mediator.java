package com.telco.platform.mediator;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Event;
import com.telco.platform.cqrs.Query;

/**
 * Central dispatcher routing commands, queries, and events through the pipeline to their handlers.
 */
public interface Mediator {

    /** Dispatches a command through the pipeline and returns its result. */
    <R> R send(Command<R> command);

    /** Dispatches a query through the pipeline and returns its result. */
    <R> R query(Query<R> query);

    /** Dispatches an event to all registered handlers; a no-op when none exist. */
    void publish(Event event);
}
