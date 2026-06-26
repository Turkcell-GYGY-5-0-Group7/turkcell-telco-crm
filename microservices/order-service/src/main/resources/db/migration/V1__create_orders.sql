-- order-service: orders aggregate root (ADR-006, ADR-016).
-- Platform tables (outbox V900) are added from classpath:db/migration/platform.
-- NOTE: ORDER is a reserved SQL word; the table is named "orders" (plural) which is safe.

CREATE TABLE IF NOT EXISTS orders (
    id                  UUID            PRIMARY KEY,
    customer_id         UUID            NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    idempotency_key     VARCHAR(64)     NOT NULL UNIQUE,
    total_amount        NUMERIC(12, 2),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id     ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status          ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency_key ON orders (idempotency_key);
