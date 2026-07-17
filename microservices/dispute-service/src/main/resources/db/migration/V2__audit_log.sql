-- dispute-service is audit-mandated (ADR-028 Section 2, ADR-021, NFR-12). Mirrors the audit_log
-- shape already used by identity/customer/subscription/payment/order-service.

CREATE TABLE IF NOT EXISTS audit_log (
    id             UUID         PRIMARY KEY,
    actor_id       UUID,
    action         VARCHAR(128) NOT NULL,
    entity         VARCHAR(128) NOT NULL,
    entity_id      VARCHAR(255),
    details        JSONB,
    correlation_id VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dispute_audit_log_entity_id ON audit_log (entity_id);
CREATE INDEX IF NOT EXISTS idx_dispute_audit_log_created_at ON audit_log (created_at);
