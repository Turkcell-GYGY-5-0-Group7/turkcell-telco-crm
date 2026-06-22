package com.telco.platform.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port for outbox rows. The JDBC implementation lives in starter-outbox.
 */
public interface OutboxStore {

    /** Inserts a new outbox row within the caller's transaction. */
    void append(OutboxRecord record);

    /** Returns up to {@code limit} rows in the given status (for the optional relay fallback). */
    List<OutboxRecord> findByStatus(OutboxStatus status, int limit);

    /** Counts rows in {@code status} created before {@code olderThan} (stuck-row monitoring). */
    int countByStatusOlderThan(OutboxStatus status, Instant olderThan);

    /** Marks the row published. */
    void markPublished(UUID id);

    /** Marks the row failed, incrementing the retry count and recording the failure reason. */
    void markFailed(UUID id, String errorMessage);
}
