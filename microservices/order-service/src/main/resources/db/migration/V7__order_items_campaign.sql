-- order-service: snapshot which campaign (if any) discounted each order line, so campaign-service can
-- correlate a later payment.completed.v1/order.cancelled.v1 event back to the specific campaign
-- redemption it should CONFIRM/RELEASE (Sprint 21 Feature 21.3.3, ADR-027 Decision Section 4 third
-- ratification addendum). Item-scoped, not order-scoped: a single order can carry items priced
-- against different campaigns. Both columns are nullable and additive - an item with no eligible
-- campaign at order-creation time simply leaves them NULL (undiscounted, today's behavior), matching
-- the tariff_id/tariff_code/tariff_version snapshot symmetry added in
-- V5__add_tariff_snapshot_to_order_items.sql (unlike that migration, no DEFAULT/backfill is needed
-- since NULL is itself the correct value for existing and future undiscounted rows).
ALTER TABLE order_items ADD COLUMN campaign_id   UUID;
ALTER TABLE order_items ADD COLUMN campaign_code VARCHAR(50);
