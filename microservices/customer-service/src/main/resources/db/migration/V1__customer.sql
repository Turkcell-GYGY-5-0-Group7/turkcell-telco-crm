-- customer-service schema (ADR-006, ADR-016). Customer master record with KYC, PII and soft-delete.
-- identity_number_enc stores AES-GCM ciphertext, never plaintext (NFR-06, ADR-021). Document binaries
-- live in MinIO; documents holds only the object reference (ADR-006). Platform tables (outbox V900)
-- are added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS customers (
    id                   UUID         PRIMARY KEY,
    type                 VARCHAR(16)  NOT NULL,
    first_name           VARCHAR(128) NOT NULL,
    last_name            VARCHAR(128) NOT NULL,
    -- AES-GCM ciphertext of the TCKN/VKN (base64). Never store the raw identity number.
    identity_number_enc  TEXT         NOT NULL,
    date_of_birth        DATE,
    status               VARCHAR(32)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Soft-delete marker (FR-04, KVKK/GDPR). NULL means active; a timestamp means deleted.
    deleted_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_customers_status ON customers (status);
-- Default reads filter on deleted_at IS NULL; index the live rows.
CREATE INDEX IF NOT EXISTS idx_customers_not_deleted ON customers (id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS addresses (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    line1        VARCHAR(255) NOT NULL,
    city         VARCHAR(128) NOT NULL,
    district     VARCHAR(128),
    postal_code  VARCHAR(16),
    is_default   BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_addresses_customer_id ON addresses (customer_id);
-- At most one default address per customer (FR-03, enforced at the database level).
CREATE UNIQUE INDEX IF NOT EXISTS uq_addresses_one_default
    ON addresses (customer_id) WHERE is_default;

CREATE TABLE IF NOT EXISTS documents (
    id            UUID         PRIMARY KEY,
    customer_id   UUID         NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    type          VARCHAR(32)  NOT NULL,
    -- MinIO object reference (key within the KYC bucket). The binary is never stored in the database.
    file_ref      VARCHAR(512) NOT NULL,
    content_type  VARCHAR(128),
    checksum      VARCHAR(128),
    verified_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_documents_customer_id ON documents (customer_id);

-- Audit trail for every state-changing operation (NFR-12, ADR-021). Mirrors the identity-service
-- audit shape, including correlation_id so each row traces back to the originating request.
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
