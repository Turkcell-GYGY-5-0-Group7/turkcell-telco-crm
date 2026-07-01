package com.telco.platform.mediator.pipeline;

/**
 * Ordering constants for pipeline behaviors. Lower values wrap further outside (run first);
 * higher values sit closer to the handler.
 */
public final class PipelineOrder {

    public static final int VALIDATION = 100;
    public static final int AUTHORIZATION = 200;
    public static final int LOGGING = 300;
    public static final int TRANSACTION = 400;
    // INBOX must sit INSIDE the transaction (order > TRANSACTION) so the inbox dedup INSERT and the
    // handler's writes commit/roll back together (exactly-once-effect, ADR-005). It still wraps the
    // handler invocation (order < PERFORMANCE) so a duplicate short-circuits before the handler runs.
    public static final int INBOX = 450;
    public static final int PERFORMANCE = 500;
    public static final int DEFAULT = 1000;

    private PipelineOrder() {
    }
}
