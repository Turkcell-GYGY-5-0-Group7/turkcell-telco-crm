-- Platform transactional outbox table (ADR-005, ADR-009, ADR-016).
-- Debezium outbox-event-router friendly. Versioned at V900+ to avoid collision with service migrations.
-- Services include this via: spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform

CREATE TABLE IF NOT EXISTS outbox_event (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB        NOT NULL,
    headers         JSONB,
    trace_id        VARCHAR(128),
    correlation_id  VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status          VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created_at
    ON outbox_event (status, created_at);
