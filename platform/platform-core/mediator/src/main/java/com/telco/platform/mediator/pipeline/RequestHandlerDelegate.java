package com.telco.platform.mediator.pipeline;

/**
 * The continuation of a pipeline: invoking it runs the next behavior or, ultimately, the handler.
 *
 * @param <R> the result type
 */
@FunctionalInterface
public interface RequestHandlerDelegate<R> {

    /** Proceeds to the next stage of the pipeline. */
    R invoke();
}
