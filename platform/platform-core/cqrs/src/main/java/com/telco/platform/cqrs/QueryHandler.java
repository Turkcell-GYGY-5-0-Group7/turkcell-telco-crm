package com.telco.platform.cqrs;

/**
 * Handles a single {@link Query} type.
 *
 * @param <Q> the query type
 * @param <R> the result type
 */
public interface QueryHandler<Q extends Query<R>, R> {

    /** Executes the query and returns its result. */
    R handle(Q query);
}
