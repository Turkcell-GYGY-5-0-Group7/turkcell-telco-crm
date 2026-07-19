-- FR-09/FR-22: addons attached to a subscription via ADDON orders. The fee bills on the next
-- monthly invoice (billing-service consumes subscription.addon-attached.v1).
CREATE TABLE IF NOT EXISTS subscription_addons (
    id               UUID          PRIMARY KEY,
    subscription_id  UUID          NOT NULL,
    order_id         UUID          NOT NULL,
    addon_code       VARCHAR(50)   NOT NULL,
    addon_type       VARCHAR(20),
    price            NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    attached_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_subscription_addons_subscription_id
    ON subscription_addons (subscription_id);
-- Second line of defense behind inbox dedup: one order attaches a given addon exactly once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_addons_order_addon
    ON subscription_addons (order_id, addon_code);
