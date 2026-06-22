package com.telco.platform.common.context;

/**
 * Immutable carrier of trace/correlation identifiers for the current request.
 *
 * @param traceId       distributed-trace identifier
 * @param correlationId end-to-end correlation identifier
 */
public record CorrelationContext(String traceId, String correlationId) {
}
