-- campaign-service schema (ADR-006, ADR-016, ADR-027).
-- Campaign + CampaignRedemption aggregate family, isolated from product-catalog-db and order-db:
-- campaigns.applicable tariff codes are opaque references (never a copy of tariff pricing data).
-- Platform tables (outbox_event V900, inbox_message V901) are added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS campaigns (
    id                          UUID           PRIMARY KEY,
    code                        VARCHAR(50)    NOT NULL UNIQUE,
    name                        VARCHAR(200)   NOT NULL,
    description                 TEXT,
    discount_type               VARCHAR(20)    NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    discount_value              NUMERIC(12, 2) NOT NULL,
    valid_from                  TIMESTAMPTZ    NOT NULL,
    valid_to                    TIMESTAMPTZ    NOT NULL,
    status                      VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
                                                CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED', 'CANCELLED')),
    total_redemption_cap        INT,
    per_customer_redemption_cap INT            NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                     INT            NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaigns_code ON campaigns (code);
CREATE INDEX IF NOT EXISTS idx_campaigns_status ON campaigns (status);

-- Normalized child table for admin-curated, opaque product-catalog tariff codes a campaign targets
-- (preferred over an array/CSV column on campaigns for query-ability and referential clarity;
-- ADR-027 Decision Section 3 - stores codes only, never tariff price data).
CREATE TABLE IF NOT EXISTS campaign_tariff_codes (
    campaign_id  UUID        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    tariff_code  VARCHAR(50) NOT NULL,
    PRIMARY KEY (campaign_id, tariff_code)
);

CREATE TABLE IF NOT EXISTS campaign_redemptions (
    id            UUID        PRIMARY KEY,
    campaign_id   UUID        NOT NULL REFERENCES campaigns (id),
    customer_id   UUID        NOT NULL,
    order_id      UUID,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED')),
    redeemed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at  TIMESTAMPTZ
);

-- Per-customer cap lookups.
CREATE INDEX IF NOT EXISTS idx_campaign_redemptions_campaign_customer
    ON campaign_redemptions (campaign_id, customer_id);
-- Total-cap counting (CONFIRMED + still-live RESERVED rows).
CREATE INDEX IF NOT EXISTS idx_campaign_redemptions_campaign_status
    ON campaign_redemptions (campaign_id, status);
-- Correlation lookups from 21.4's order/payment event consumers.
CREATE INDEX IF NOT EXISTS idx_campaign_redemptions_order_id
    ON campaign_redemptions (order_id);
