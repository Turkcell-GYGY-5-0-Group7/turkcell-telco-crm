package com.telco.platform.common.logging;

/**
 * Sink for {@link ExceptionLogEntry} records. A JDBC implementation is provided by
 * {@code starter-log-persistence} and is opt-in (local/test environments). When no writer bean is
 * present, the global exception handler simply omits {@code logId} and relies on traceId.
 */
public interface ExceptionLogWriter {

    /** Persists or forwards the exception log entry. Implementations MUST NOT throw. */
    void write(ExceptionLogEntry entry);
}
