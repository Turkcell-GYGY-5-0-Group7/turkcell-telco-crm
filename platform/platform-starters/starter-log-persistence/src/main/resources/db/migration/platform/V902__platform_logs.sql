-- Optional platform log-persistence tables (local/test environments).
-- Created only when telco.platform.logging.persistence.enabled=true and these migrations are on the
-- classpath. Production uses structured logs to Loki plus traceId/correlationId (ADR-012, ADR-015).
-- Services include this via: spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform

CREATE TABLE IF NOT EXISTS request_logs (
    id              BIGSERIAL    PRIMARY KEY,
    service         VARCHAR(128) NOT NULL,
    request_type    VARCHAR(255) NOT NULL,
    request_kind    VARCHAR(32)  NOT NULL,
    user_id         VARCHAR(128),
    correlation_id  VARCHAR(128),
    duration_ms     BIGINT       NOT NULL,
    success         BOOLEAN      NOT NULL,
    error_code      VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_request_logs_created_at ON request_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_request_logs_correlation ON request_logs (correlation_id);

CREATE TABLE IF NOT EXISTS exception_logs (
    id              UUID         PRIMARY KEY,
    service         VARCHAR(128) NOT NULL,
    path            VARCHAR(512),
    exception_type  VARCHAR(512) NOT NULL,
    message         TEXT,
    stack_trace     TEXT,
    status_code     INTEGER      NOT NULL,
    error_code      VARCHAR(128),
    trace_id        VARCHAR(128),
    correlation_id  VARCHAR(128),
    user_id         VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_exception_logs_created_at ON exception_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_exception_logs_correlation ON exception_logs (correlation_id);
