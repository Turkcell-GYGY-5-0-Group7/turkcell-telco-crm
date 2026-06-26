-- payment-service schema (ADR-006, ADR-016).
-- Payment aggregate root table. Platform tables (outbox/inbox V900) are added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS payments (
    id                  UUID            PRIMARY KEY,
    order_id            UUID            NOT NULL UNIQUE,
    customer_id         UUID            NOT NULL,
    amount              NUMERIC(12, 2)  NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    payment_request_id  VARCHAR(64)     NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id            ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status              ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_payment_request_id  ON payments (payment_request_id);
CREATE INDEX IF NOT EXISTS idx_payments_status_created_at   ON payments (status, created_at);
