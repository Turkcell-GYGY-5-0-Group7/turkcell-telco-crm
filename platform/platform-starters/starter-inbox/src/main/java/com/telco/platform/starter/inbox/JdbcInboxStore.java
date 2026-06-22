package com.telco.platform.starter.inbox;

import com.telco.platform.inbox.InboxStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link InboxStore} backed by {@link JdbcTemplate}. Idempotency is enforced by the table's
 * composite primary key {@code (message_id, handler)} together with {@code ON CONFLICT DO NOTHING}:
 * an insert that affects one row is the first sighting; zero rows means a duplicate.
 */
public final class JdbcInboxStore implements InboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final String insertSql;

    public JdbcInboxStore(JdbcTemplate jdbcTemplate, String table) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertSql = """
                INSERT INTO %s (message_id, handler)
                VALUES (?, ?)
                ON CONFLICT (message_id, handler) DO NOTHING
                """.formatted(table);
    }

    @Override
    public boolean markProcessed(String messageId, String handler) {
        return jdbcTemplate.update(insertSql, messageId, handler) == 1;
    }
}
