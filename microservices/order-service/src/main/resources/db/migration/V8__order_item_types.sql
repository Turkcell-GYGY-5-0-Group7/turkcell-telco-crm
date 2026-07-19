-- order-service: generalize the order model beyond single-tariff new-line orders (Sprint 24
-- Feature 24.2, design-note D1/D2, FR-09/FR-22).
--
-- orders.order_type persists the kind of order derived from its items at creation time
-- (NEW_LINE | ADDON | PLAN_CHANGE) so saga consumers can branch without re-deriving.
-- Existing rows are all single-tariff onboarding orders -> NEW_LINE backfill via DEFAULT, and the
-- DEFAULT is kept: NEW_LINE is the correct fallback for any writer that predates this migration.
ALTER TABLE orders ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'NEW_LINE';

-- order_items.item_type discriminates TARIFF vs ADDON line items. ADDON items carry a
-- product_code (catalog addon code), an optional target_subscription_id (standalone addon
-- purchases only; NULL for addons bundled into a NEW_LINE order) and an allowance snapshot taken
-- at order-creation time (immutable-event precedent of V5's tariff snapshot: usage-service must
-- never need a runtime catalog hop to apply the delta). PLAN_CHANGE tariff items reuse
-- target_subscription_id for the subscription whose tariff changes.
ALTER TABLE order_items ADD COLUMN item_type              VARCHAR(20) NOT NULL DEFAULT 'TARIFF';
ALTER TABLE order_items ADD COLUMN product_code           VARCHAR(64);
ALTER TABLE order_items ADD COLUMN target_subscription_id UUID;
ALTER TABLE order_items ADD COLUMN allowance_data_mb      BIGINT;
ALTER TABLE order_items ADD COLUMN allowance_minutes      BIGINT;
ALTER TABLE order_items ADD COLUMN allowance_sms          BIGINT;

-- ADDON items carry no tariff, so the tariff snapshot columns become nullable. tariff_id was
-- NOT NULL since V2; tariff_code/tariff_version since V5 (tariff_name and unit_price were always
-- nullable). The application enforces that TARIFF items still populate all of them.
ALTER TABLE order_items ALTER COLUMN tariff_id      DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN tariff_code    DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN tariff_version DROP NOT NULL;
