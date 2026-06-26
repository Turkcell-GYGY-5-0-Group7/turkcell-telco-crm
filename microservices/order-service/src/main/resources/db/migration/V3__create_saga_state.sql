-- order-service: saga_state table for saga orchestration tracking (ADR-006, ADR-016).

CREATE TABLE IF NOT EXISTS saga_state (
    id          UUID        PRIMARY KEY,
    order_id    UUID        NOT NULL UNIQUE,
    step        VARCHAR(64) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    payload     JSONB,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_saga_state_order_id ON saga_state (order_id);
CREATE INDEX IF NOT EXISTS idx_saga_state_status   ON saga_state (status);
