package com.telco.platform.cqrs;

/**
 * Handles a single {@link Command} type.
 *
 * @param <C> the command type
 * @param <R> the result type
 */
public interface CommandHandler<C extends Command<R>, R> {

    /** Executes the command and returns its result. */
    R handle(C command);
}
