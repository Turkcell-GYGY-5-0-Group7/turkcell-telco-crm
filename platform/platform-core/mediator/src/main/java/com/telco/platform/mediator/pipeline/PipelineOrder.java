package com.telco.platform.mediator.pipeline;

/**
 * Ordering constants for pipeline behaviors. Lower values wrap further outside (run first);
 * higher values sit closer to the handler.
 */
public final class PipelineOrder {

    public static final int VALIDATION = 100;
    public static final int AUTHORIZATION = 200;
    public static final int LOGGING = 300;
    public static final int INBOX = 350;
    public static final int TRANSACTION = 400;
    public static final int PERFORMANCE = 500;
    public static final int DEFAULT = 1000;

    private PipelineOrder() {
    }
}
