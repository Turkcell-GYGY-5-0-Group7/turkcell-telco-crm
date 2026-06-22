package com.telco.platform.starter.logpersistence;

import com.telco.platform.mediator.behavior.support.RequestLogEntry;
import com.telco.platform.mediator.behavior.support.RequestLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * Persists mediator {@link RequestLogEntry} rows to {@code request_logs} for local/test debugging.
 * Failures are swallowed so logging never breaks request handling.
 */
public final class JdbcRequestLogWriter implements RequestLogWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcRequestLogWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final String insertSql;

    public JdbcRequestLogWriter(JdbcTemplate jdbcTemplate, String table) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSql = """
                INSERT INTO %s
                    (service, request_type, request_kind, user_id, correlation_id,
                     duration_ms, success, error_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table);
    }

    @Override
    public void write(RequestLogEntry entry) {
        try {
            jdbcTemplate.update(insertSql,
                    entry.service(),
                    entry.requestType(),
                    entry.requestKind(),
                    entry.userId(),
                    entry.correlationId(),
                    entry.durationMs(),
                    entry.success(),
                    entry.errorCode(),
                    entry.timestamp() == null ? null : Timestamp.from(entry.timestamp()));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to persist request log: {}", ex.getMessage());
        }
    }
}
