package com.telco.platform.common.context;

/**
 * Shared header and MDC key names for correlation and user propagation.
 */
public final class CorrelationConstants {

    /** HTTP header carrying the end-to-end correlation id. */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    /** MDC key for the trace id. */
    public static final String MDC_TRACE_ID = "traceId";
    /** MDC key for the correlation id. */
    public static final String MDC_CORRELATION_ID = "correlationId";
    /** HTTP header carrying the gateway-trusted user id. */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** HTTP header carrying the gateway-trusted comma-separated roles. */
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    /** HTTP header carrying the gateway-trusted, resolved customer-service {@code customerId}. */
    public static final String HEADER_CUSTOMER_ID = "X-Customer-Id";

    private CorrelationConstants() {
    }
}
