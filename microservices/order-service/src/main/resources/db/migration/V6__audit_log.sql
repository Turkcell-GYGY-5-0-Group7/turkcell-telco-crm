-- Audit trail for every state-changing operation (NFR-12, ADR-021). order-service is one of the
-- four audit-mandated services. Mirrors the subscription/identity audit shape.
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

CREATE INDEX IF NOT EXISTS idx_order_audit_log_entity_id ON audit_log (entity_id);
CREATE INDEX IF NOT EXISTS idx_order_audit_log_created_at ON audit_log (created_at);
