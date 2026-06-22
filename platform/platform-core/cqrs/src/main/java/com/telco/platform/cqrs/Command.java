package com.telco.platform.cqrs;

/**
 * A state-changing request. Use {@code Command<Unit>} for commands without a return value.
 *
 * @param <R> the result type
 */
public interface Command<R> extends Request<R> {
}
