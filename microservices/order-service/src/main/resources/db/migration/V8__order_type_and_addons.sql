-- FR-09: order types and addon line items.
-- Existing rows are all pre-FR-09 new-line orders, so NEW_LINE is the correct backfill.
ALTER TABLE orders
    ADD COLUMN order_type VARCHAR(32) NOT NULL DEFAULT 'NEW_LINE',
    ADD COLUMN subscription_id UUID;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_type
        CHECK (order_type IN ('NEW_LINE', 'PLAN_CHANGE', 'ADDON')),
    ADD CONSTRAINT chk_orders_subscription
        CHECK (order_type = 'NEW_LINE' OR subscription_id IS NOT NULL);

-- Addon line items carry no tariff reference; every item must reference exactly one product kind.
ALTER TABLE order_items
    ADD COLUMN addon_code VARCHAR(50),
    ALTER COLUMN tariff_id DROP NOT NULL,
    ALTER COLUMN tariff_code DROP NOT NULL;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_product
        CHECK (tariff_id IS NOT NULL OR addon_code IS NOT NULL);
