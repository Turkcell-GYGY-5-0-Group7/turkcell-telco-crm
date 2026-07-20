-- FR-22: addon fees recorded from subscription.addon-attached.v1, billed once on the next
-- monthly invoice as ADDON/VAS lines and then marked billed.
CREATE TABLE IF NOT EXISTS addon_charges (
    id               UUID          PRIMARY KEY,
    subscription_id  UUID          NOT NULL,
    order_id         UUID          NOT NULL,
    addon_code       VARCHAR(50)   NOT NULL,
    addon_type       VARCHAR(20),
    price            NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    attached_at      TIMESTAMPTZ   NOT NULL,
    billed           BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_addon_charges_subscription_unbilled
    ON addon_charges (subscription_id) WHERE billed = FALSE;
-- Second line of defense behind inbox dedup: one order's addon is charged at most once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_addon_charges_order_addon
    ON addon_charges (order_id, addon_code);
