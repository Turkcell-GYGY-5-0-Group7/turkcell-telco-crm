package com.telco.platform.mediator.pipeline;

/**
 * A cross-cutting stage wrapped around request handling (validation, auth, logging, etc.).
 * Behaviors form a chain ordered by {@link #order()}; lower orders wrap outermost.
 */
public interface PipelineBehavior {

    /** Whether this behavior applies to the given request. */
    boolean supports(Object request);

    /** Performs the behavior and delegates to {@code next} to continue the pipeline. */
    <R> R handle(Object request, RequestHandlerDelegate<R> next);

    /** Ordering rank; lower runs first (outermost). */
    default int order() {
        return PipelineOrder.DEFAULT;
    }
}
