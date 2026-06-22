package com.telco.platform.starter.logpersistence;

import com.telco.platform.common.context.CorrelationContextHolder;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;
import com.telco.platform.mediator.behavior.support.RequestLogEntry;
import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Persists one request-log row per HTTP request, for services that do not route through the
 * mediator (for example simple-service-layer services). Opt-in and intended for local/test use;
 * actuator endpoints are skipped.
 */
public final class HttpRequestLogFilter extends OncePerRequestFilter {

    private final String serviceName;
    private final List<RequestLogWriter> writers;

    public HttpRequestLogFilter(String serviceName, List<RequestLogWriter> writers) {
        this.serviceName = serviceName;
        this.writers = writers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        boolean success = true;
        try {
            filterChain.doFilter(request, response);
            success = response.getStatus() < 500;
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            RequestLogEntry entry = new RequestLogEntry(
                    serviceName,
                    request.getMethod() + " " + request.getRequestURI(),
                    "HTTP",
                    UserContextHolder.get().map(UserContext::userId).orElse(null),
                    CorrelationContextHolder.get().map(c -> c.correlationId()).orElse(null),
                    System.currentTimeMillis() - start,
                    success,
                    null,
                    Instant.now());
            writers.forEach(writer -> writer.write(entry));
        }
    }
}
