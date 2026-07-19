-- billing-service: addon charge read model (Sprint 24 Feature 24.3, design-note D3, FR-22).
--
-- One row per addon purchase, recorded from addon.purchased.v1. price is the FULL amount of the
-- purchase (unit price * quantity, as charged by the order saga). The next bill run adds exactly
-- one invoice line per unbilled row and flips billed inside the bill-run transaction, so the line
-- appears exactly once (the invoice is the recurring bill of record; the upfront saga charge plus
-- this line is the accepted tariff precedent, not a double-charge bug - design-note D3).
CREATE TABLE IF NOT EXISTS addon_charge_records (
    id              UUID            PRIMARY KEY,
    subscription_id UUID            NOT NULL,
    customer_id     UUID            NOT NULL,
    addon_code      VARCHAR(64)     NOT NULL,
    addon_name      VARCHAR(255),
    price           NUMERIC(19, 4)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL DEFAULT 'TRY',
    purchased_at    TIMESTAMPTZ     NOT NULL,
    billed          BOOLEAN         NOT NULL DEFAULT FALSE,
    invoice_id      UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- The bill run scans per subscription for unbilled charges only.
CREATE INDEX IF NOT EXISTS idx_addon_charges_sub_unbilled
    ON addon_charge_records (subscription_id) WHERE NOT billed;
