package com.telco.platform.starter.outbox;

import com.telco.platform.outbox.OutboxRecord;
import com.telco.platform.outbox.OutboxStatus;
import com.telco.platform.outbox.OutboxStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link OutboxStore} backed by {@link JdbcTemplate}. Because {@code JdbcTemplate} joins the ambient
 * Spring transaction, {@link #append(OutboxRecord)} commits atomically with the caller's business
 * writes (transactional outbox). The {@code payload}/{@code headers} columns are {@code jsonb} and
 * are written with an explicit {@code ::jsonb} cast.
 */
public final class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    private final String insertSql;
    private final String findByStatusSql;
    private final String countStaleSql;
    private final String markPublishedSql;
    private final String markFailedSql;

    public JdbcOutboxStore(JdbcTemplate jdbcTemplate, String table) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
        this.insertSql = """
                INSERT INTO %s
                    (id, aggregate_type, aggregate_id, event_type, payload, headers,
                     trace_id, correlation_id, created_at, status, retry_count, error_message)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """.formatted(table);
        this.findByStatusSql = """
                SELECT id, aggregate_type, aggregate_id, event_type, payload, headers,
                       trace_id, correlation_id, created_at, status, retry_count, error_message
                FROM %s
                WHERE status = ?
                ORDER BY created_at ASC
                LIMIT ?
                """.formatted(table);
        this.countStaleSql =
                "SELECT count(*) FROM %s WHERE status = ? AND created_at < ?".formatted(table);
        this.markPublishedSql =
                "UPDATE %s SET status = ?, error_message = NULL WHERE id = ?".formatted(table);
        this.markFailedSql =
                "UPDATE %s SET status = ?, retry_count = retry_count + 1, error_message = ? WHERE id = ?"
                        .formatted(table);
    }

    @Override
    public void append(OutboxRecord record) {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(insertSql);
            ps.setObject(1, record.id());
            ps.setString(2, record.aggregateType());
            ps.setString(3, record.aggregateId());
            ps.setString(4, record.eventType());
            ps.setString(5, record.payload());
            if (record.headers() == null) {
                ps.setNull(6, Types.OTHER);
            } else {
                ps.setString(6, record.headers());
            }
            ps.setString(7, record.traceId());
            ps.setString(8, record.correlationId());
            ps.setTimestamp(9, record.createdAt() == null ? null : Timestamp.from(record.createdAt()));
            ps.setString(10, record.status().name());
            ps.setInt(11, record.retryCount());
            ps.setString(12, record.errorMessage());
            return ps;
        });
    }

    @Override
    public List<OutboxRecord> findByStatus(OutboxStatus status, int limit) {
        return jdbcTemplate.query(findByStatusSql, OUTBOX_ROW_MAPPER, status.name(), limit);
    }

    @Override
    public int countByStatusOlderThan(OutboxStatus status, Instant olderThan) {
        Integer count = jdbcTemplate.queryForObject(
                countStaleSql, Integer.class, status.name(), Timestamp.from(olderThan));
        return count == null ? 0 : count;
    }

    @Override
    public void markPublished(UUID id) {
        jdbcTemplate.update(markPublishedSql, OutboxStatus.PUBLISHED.name(), id);
    }

    @Override
    public void markFailed(UUID id, String errorMessage) {
        jdbcTemplate.update(markFailedSql, OutboxStatus.FAILED.name(), errorMessage, id);
    }

    private static final RowMapper<OutboxRecord> OUTBOX_ROW_MAPPER = (rs, rowNum) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("headers"),
                rs.getString("trace_id"),
                rs.getString("correlation_id"),
                createdAt == null ? null : createdAt.toInstant(),
                OutboxStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_count"),
                rs.getString("error_message"));
    };

    String table() {
        return table;
    }
}
