package com.telco.platform.cqrs;

/**
 * Handles a single {@link Event} type.
 *
 * @param <E> the event type
 */
public interface EventHandler<E extends Event> {

    /** Reacts to the event. */
    void handle(E event);
}
