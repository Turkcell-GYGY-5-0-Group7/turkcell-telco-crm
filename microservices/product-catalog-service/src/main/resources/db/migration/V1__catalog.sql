-- product-catalog-service schema (ADR-006, ADR-016).
-- Tariff catalog with versioning, addons, and join table.
-- Platform tables (outbox V900) are added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS tariffs (
    id                  UUID           PRIMARY KEY,
    code                VARCHAR(50)    NOT NULL UNIQUE,
    name                VARCHAR(200)   NOT NULL,
    type                VARCHAR(20)    NOT NULL CHECK (type IN ('POSTPAID', 'PREPAID', 'HYBRID')),
    status              VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    monthly_fee         NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    minutes_included    INT            NOT NULL DEFAULT 0,
    sms_included        INT            NOT NULL DEFAULT 0,
    data_mb_included    INT            NOT NULL DEFAULT 0,
    target_segment      VARCHAR(100),
    effective_from      TIMESTAMPTZ    NOT NULL,
    effective_to        TIMESTAMPTZ,
    version             INT            NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tariffs_code ON tariffs (code);
CREATE INDEX IF NOT EXISTS idx_tariffs_status ON tariffs (status);
CREATE INDEX IF NOT EXISTS idx_tariffs_effective_window ON tariffs (status, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS addons (
    id              UUID           PRIMARY KEY,
    code            VARCHAR(50)    NOT NULL UNIQUE,
    name            VARCHAR(200)   NOT NULL,
    price           NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    type            VARCHAR(20)    NOT NULL CHECK (type IN ('DATA', 'SMS', 'MINUTES', 'VAS')),
    validity_days   INT            NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_addons_code ON addons (code);

CREATE TABLE IF NOT EXISTS tariff_addons (
    tariff_id   UUID NOT NULL REFERENCES tariffs (id) ON DELETE CASCADE,
    addon_id    UUID NOT NULL REFERENCES addons (id) ON DELETE CASCADE,
    PRIMARY KEY (tariff_id, addon_id)
);

CREATE TABLE IF NOT EXISTS tariff_versions (
    id                  UUID           PRIMARY KEY,
    tariff_code         VARCHAR(50)    NOT NULL,
    version             INT            NOT NULL,
    monthly_fee         NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    minutes_included    INT            NOT NULL DEFAULT 0,
    sms_included        INT            NOT NULL DEFAULT 0,
    data_mb_included    INT            NOT NULL DEFAULT 0,
    effective_from      TIMESTAMPTZ    NOT NULL,
    effective_to        TIMESTAMPTZ,
    snapshot_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (tariff_code, version)
);

CREATE INDEX IF NOT EXISTS idx_tariff_versions_code ON tariff_versions (tariff_code);
CREATE INDEX IF NOT EXISTS idx_tariff_versions_code_effective ON tariff_versions (tariff_code, effective_from);
