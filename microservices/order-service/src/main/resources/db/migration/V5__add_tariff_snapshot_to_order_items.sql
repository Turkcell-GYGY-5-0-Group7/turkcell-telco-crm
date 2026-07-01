-- order-service: snapshot the tariff code + version on each order line so subscription-service can
-- read them on activation without a second catalog hop (Sprint 09 Feature 9.4, single-item MVP).
-- Existing rows (none in fresh dev/test) get safe sentinels via DEFAULT, then the DEFAULT is dropped
-- so new rows must supply real values from the application.
ALTER TABLE order_items ADD COLUMN tariff_code    VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE order_items ADD COLUMN tariff_version INT         NOT NULL DEFAULT 0;

ALTER TABLE order_items ALTER COLUMN tariff_code    DROP DEFAULT;
ALTER TABLE order_items ALTER COLUMN tariff_version DROP DEFAULT;
