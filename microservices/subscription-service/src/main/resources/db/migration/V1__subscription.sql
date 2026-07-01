-- subscription-service schema (ADR-006, ADR-016). Subscription lifecycle plus MSISDN and SIM-card
-- inventory (FR-13, FR-15). Platform tables (outbox V900, inbox V901) are added from
-- classpath:db/migration/platform via the starter-outbox / starter-inbox migrations.

-- Subscription master record. status follows ACTIVE/SUSPENDED/TERMINATED (FR-13). tariff_code +
-- tariff_version pin the catalog offering version active at activation (FR-15, ADR catalog versioning).
CREATE TABLE IF NOT EXISTS subscriptions (
    id              UUID         PRIMARY KEY,
    customer_id     UUID         NOT NULL,
    msisdn          VARCHAR(20)  NOT NULL,
    tariff_code     VARCHAR(64)  NOT NULL,
    tariff_version  INTEGER      NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    activated_at    TIMESTAMPTZ,
    terminated_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_subscriptions_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_customer_id ON subscriptions (customer_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions (status);
-- One live (non-terminated) subscription per MSISDN; a number may be reused after termination.
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_active_msisdn
    ON subscriptions (msisdn) WHERE status <> 'TERMINATED';

-- MSISDN inventory pool. status follows FREE/RESERVED/ALLOCATED. reserved_until bounds a RESERVED
-- hold so abandoned onboarding flows release the number (FR-13).
CREATE TABLE IF NOT EXISTS msisdn_pool (
    msisdn          VARCHAR(20)  PRIMARY KEY,
    status          VARCHAR(16)  NOT NULL DEFAULT 'FREE',
    reserved_until  TIMESTAMPTZ,
    CONSTRAINT chk_msisdn_pool_status CHECK (status IN ('FREE', 'RESERVED', 'ALLOCATED'))
);

-- Allocation scans the pool for FREE numbers; index the live candidates.
CREATE INDEX IF NOT EXISTS idx_msisdn_pool_status ON msisdn_pool (status);

-- SIM-card inventory. iccid is the physical card id; imsi and msisdn link it to network identity and
-- the assigned number once activated (FR-15).
CREATE TABLE IF NOT EXISTS sim_cards (
    iccid   VARCHAR(22)  PRIMARY KEY,
    imsi    VARCHAR(15)  NOT NULL,
    msisdn  VARCHAR(20),
    status  VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE'
);

CREATE INDEX IF NOT EXISTS idx_sim_cards_msisdn ON sim_cards (msisdn);
CREATE INDEX IF NOT EXISTS idx_sim_cards_status ON sim_cards (status);

-- Audit trail for every state-changing operation (NFR-12, ADR-021). subscription is one of the four
-- audit-mandated services. Mirrors the customer/identity audit shape, including correlation_id so each
-- row traces back to the originating request.
CREATE TABLE IF NOT EXISTS audit_log (
    id              UUID         PRIMARY KEY,
    actor_id        UUID,
    action          VARCHAR(128) NOT NULL,
    entity          VARCHAR(128) NOT NULL,
    entity_id       VARCHAR(255),
    details         JSONB,
    correlation_id  VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_id ON audit_log (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
