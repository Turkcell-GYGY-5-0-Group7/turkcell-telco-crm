-- order-service: complete the ADDON item catalog snapshot (Sprint 24 Feature 24.3, design-note
-- D1/D3, FR-09/FR-22).
--
-- addon.purchased.v1 is published at fulfillment time from the persisted order item alone (V5
-- immutable-snapshot precedent: no runtime catalog hop at publish time), and the event contract
-- carries the addon's catalog category and price currency. V8 snapshotted the allowances but not
-- these two fields, so they are added here and populated at order-creation time from the catalog
-- addon snapshot. Both stay NULL on TARIFF items and on ADDON rows created before this migration
-- (the event fields are nullable; consumers fall back to TRY for a missing currency).
ALTER TABLE order_items ADD COLUMN addon_type VARCHAR(20);
ALTER TABLE order_items ADD COLUMN currency   VARCHAR(3);
