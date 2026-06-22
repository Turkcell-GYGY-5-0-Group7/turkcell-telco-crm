package com.telco.platform.starter.observability;

import java.io.IOException;
import java.util.UUID;

import com.telco.platform.common.context.CorrelationConstants;
import com.telco.platform.common.context.CorrelationContext;
import com.telco.platform.common.context.CorrelationContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the per-request correlation context (ADR-012, ADR-015).
 *
 * <p>Reads the inbound correlation header (default {@code X-Correlation-Id}); when absent a UUID is
 * generated. A traceId is resolved from the SLF4J MDC ({@code traceId}) when a tracer has already
 * populated it, otherwise it falls back to the correlationId so logs always carry both fields. The
 * resolved values are bound to {@link CorrelationContextHolder} and the SLF4J MDC, the correlation
 * header is echoed on the response, and both stores are always cleared once the request completes.
 *
 * <p>The filter registers with high precedence so downstream filters, controllers, and logging see
 * the context. It carries no business logic.
 */
public class CorrelationFilter extends OncePerRequestFilter {

    private final String correlationHeader;

    /**
     * Creates a filter bound to the configured correlation header name.
     *
     * @param correlationHeader inbound/outbound correlation header name
     */
    public CorrelationFilter(String correlationHeader) {
        this.correlationHeader = StringUtils.hasText(correlationHeader)
                ? correlationHeader
                : CorrelationConstants.HEADER_CORRELATION_ID;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        String traceId = resolveTraceId(correlationId);

        CorrelationContextHolder.set(new CorrelationContext(traceId, correlationId));
        MDC.put(CorrelationConstants.MDC_TRACE_ID, traceId);
        MDC.put(CorrelationConstants.MDC_CORRELATION_ID, correlationId);
        response.setHeader(correlationHeader, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationConstants.MDC_TRACE_ID);
            MDC.remove(CorrelationConstants.MDC_CORRELATION_ID);
            CorrelationContextHolder.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(correlationHeader);
        return StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
    }

    /**
     * Prefers a traceId already placed in the MDC by an active tracer (for example
     * micrometer-tracing); falls back to the correlationId so log lines always carry a traceId.
     */
    private String resolveTraceId(String correlationId) {
        String tracerTraceId = MDC.get(CorrelationConstants.MDC_TRACE_ID);
        return StringUtils.hasText(tracerTraceId) ? tracerTraceId : correlationId;
    }
}
