package com.telco.platform.cqrs;

/**
 * Common super-type for dispatchable requests (commands and queries). Internal use.
 *
 * @param <R> the result type produced when handled
 */
public interface Request<R> {
}
