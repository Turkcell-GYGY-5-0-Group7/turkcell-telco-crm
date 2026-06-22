package com.telco.platform.cqrs;

/**
 * A read-only request that returns data without changing state.
 *
 * @param <R> the result type
 */
public interface Query<R> extends Request<R> {
}
