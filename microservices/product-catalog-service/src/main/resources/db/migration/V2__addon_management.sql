-- product-catalog-service: addon management (Sprint 24, feature 24.1, FR-05).
-- Adds nullable allowance columns to addons and seeds the standard addon catalog.
--
-- NOTE on tariff_addons links: this migration seeds no links. Addons are attached to a tariff at
-- runtime through the owning Tariff.addons side (Tariff#addAddon), which persists the
-- tariff_addons join rows; POST /api/v1/addons does not accept tariff codes.

ALTER TABLE addons
    ADD COLUMN data_mb       BIGINT,
    ADD COLUMN voice_minutes BIGINT,
    ADD COLUMN sms_count     BIGINT;

-- Fixed UUIDs keep the seed deterministic across environments.
INSERT INTO addons (id, code, name, price, currency, type, validity_days, status,
                    data_mb, voice_minutes, sms_count)
VALUES
    ('ad000000-0000-0000-0000-000000000001', 'DATA_5GB', 'Data 5 GB',
     49.90, 'TRY', 'DATA', 30, 'ACTIVE', 5120, NULL, NULL),
    ('ad000000-0000-0000-0000-000000000002', 'DATA_10GB', 'Data 10 GB',
     79.90, 'TRY', 'DATA', 30, 'ACTIVE', 10240, NULL, NULL),
    ('ad000000-0000-0000-0000-000000000003', 'SMS_500', 'SMS 500',
     19.90, 'TRY', 'SMS', 30, 'ACTIVE', NULL, NULL, 500),
    ('ad000000-0000-0000-0000-000000000004', 'VOICE_300', 'Voice 300 Minutes',
     29.90, 'TRY', 'MINUTES', 30, 'ACTIVE', NULL, 300, NULL),
    ('ad000000-0000-0000-0000-000000000005', 'VAS_CALLERTUNE', 'Caller Tune',
     9.90, 'TRY', 'VAS', 30, 'ACTIVE', NULL, NULL, NULL)
ON CONFLICT (code) DO NOTHING;
