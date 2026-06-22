package com.telco.platform.starter.logpersistence;

import com.telco.platform.common.logging.ExceptionLogEntry;
import com.telco.platform.common.logging.ExceptionLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * Persists {@link ExceptionLogEntry} rows to {@code exception_logs} for local/test debugging, so the
 * {@code logId} returned in error responses resolves to a stored row. Failures are swallowed.
 */
public final class JdbcExceptionLogWriter implements ExceptionLogWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcExceptionLogWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final String insertSql;

    public JdbcExceptionLogWriter(JdbcTemplate jdbcTemplate, String table) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSql = """
                INSERT INTO %s
                    (id, service, path, exception_type, message, stack_trace, status_code,
                     error_code, trace_id, correlation_id, user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table);
    }

    @Override
    public void write(ExceptionLogEntry entry) {
        try {
            jdbcTemplate.update(insertSql,
                    entry.id(),
                    entry.service(),
                    entry.path(),
                    entry.exceptionType(),
                    entry.message(),
                    entry.stackTrace(),
                    entry.statusCode(),
                    entry.errorCode(),
                    entry.traceId(),
                    entry.correlationId(),
                    entry.userId(),
                    entry.timestamp() == null ? null : Timestamp.from(entry.timestamp()));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to persist exception log: {}", ex.getMessage());
        }
    }
}
