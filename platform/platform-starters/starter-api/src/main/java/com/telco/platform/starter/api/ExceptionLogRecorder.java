package com.telco.platform.starter.api;

import com.telco.platform.common.context.CorrelationContextHolder;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;
import com.telco.platform.common.logging.ExceptionLogEntry;
import com.telco.platform.common.logging.ExceptionLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records handled exceptions to any configured {@link ExceptionLogWriter} and returns the generated
 * {@code logId}. When no writer is present (the default, Loki-only setup), recording is a no-op and
 * {@code null} is returned, so error responses simply carry traceId. Writers are expected not to
 * throw; any failure here is swallowed so logging never breaks the error response.
 */
final class ExceptionLogRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionLogRecorder.class);

    private final String serviceName;
    private final List<ExceptionLogWriter> writers;

    ExceptionLogRecorder(String serviceName, List<ExceptionLogWriter> writers) {
        this.serviceName = serviceName;
        this.writers = writers;
    }

    /** Persists the exception and returns its logId, or null when no writer is configured. */
    String record(Throwable ex, String path, int statusCode, String errorCode) {
        if (writers.isEmpty()) {
            return null;
        }
        UUID id = UUID.randomUUID();
        String traceId = CorrelationContextHolder.get().map(c -> c.traceId()).orElse(null);
        String correlationId = CorrelationContextHolder.get().map(c -> c.correlationId()).orElse(null);
        String userId = UserContextHolder.get().map(UserContext::userId).orElse(null);
        ExceptionLogEntry entry = new ExceptionLogEntry(id, serviceName, path, ex.getClass().getName(),
                ex.getMessage(), stackTraceOf(ex), statusCode, errorCode, traceId, correlationId, userId,
                Instant.now());
        for (ExceptionLogWriter writer : writers) {
            try {
                writer.write(entry);
            } catch (RuntimeException loggingFailure) {
                LOGGER.warn("Exception-log writer {} failed: {}", writer.getClass().getSimpleName(),
                        loggingFailure.getMessage());
            }
        }
        return id.toString();
    }

    private static String stackTraceOf(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
