-- order-service: order_items child table (ADR-006, ADR-016).

CREATE TABLE IF NOT EXISTS order_items (
    id          UUID            PRIMARY KEY,
    order_id    UUID            NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    tariff_id   UUID            NOT NULL,
    tariff_name VARCHAR(255),
    unit_price  NUMERIC(12, 2),
    quantity    INT             NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);
