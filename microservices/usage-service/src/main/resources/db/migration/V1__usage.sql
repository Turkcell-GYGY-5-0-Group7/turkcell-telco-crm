-- usage-service schema (ADR-006, ADR-016).
-- Platform tables (outbox/inbox V900) added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS quotas (
    id                          UUID            PRIMARY KEY,
    subscription_id             UUID            NOT NULL,
    customer_id                 UUID,
    period_start                TIMESTAMPTZ     NOT NULL,
    period_end                  TIMESTAMPTZ     NOT NULL,
    minutes_total               BIGINT          NOT NULL DEFAULT 0,
    sms_total                   BIGINT          NOT NULL DEFAULT 0,
    mb_total                    BIGINT          NOT NULL DEFAULT 0,
    minutes_remaining           BIGINT          NOT NULL DEFAULT 0,
    sms_remaining               BIGINT          NOT NULL DEFAULT 0,
    mb_remaining                BIGINT          NOT NULL DEFAULT 0,
    threshold_notified          BOOLEAN         NOT NULL DEFAULT FALSE,
    exceeded_notified           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_quotas_subscription_period
    ON quotas (subscription_id, period_start);
CREATE INDEX IF NOT EXISTS idx_quotas_subscription_id ON quotas (subscription_id);

CREATE TABLE IF NOT EXISTS usage_records (
    id              UUID            PRIMARY KEY,
    subscription_id UUID            NOT NULL,
    quota_id        UUID            REFERENCES quotas(id),
    type            VARCHAR(16)     NOT NULL,
    quantity        BIGINT          NOT NULL,
    overage         BOOLEAN         NOT NULL DEFAULT FALSE,
    cdr_ref         VARCHAR(128)    NOT NULL UNIQUE,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_usage_records_subscription_id  ON usage_records (subscription_id);
CREATE INDEX IF NOT EXISTS idx_usage_records_subscription_period
    ON usage_records (subscription_id, recorded_at);
CREATE UNIQUE INDEX IF NOT EXISTS uidx_usage_records_cdr_ref ON usage_records (cdr_ref);
