-- Platform inbox idempotency table (ADR-005, ADR-016).
-- Composite primary key guarantees a message is processed once per handler.
-- Services include this via: spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform

CREATE TABLE IF NOT EXISTS inbox_message (
    message_id    VARCHAR(255) NOT NULL,
    handler       VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, handler)
);
