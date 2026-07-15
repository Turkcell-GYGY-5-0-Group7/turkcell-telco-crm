-- campaign-service schema (Feature 21.4.3, ADR-027 Decision Section 4).
-- Admin-visible defensive flag set by the tariff.price-changed.v1 consumer when an ACTIVE campaign
-- references a tariff code whose price has since changed. Never auto-mutates status (chosen
-- "flag, don't auto-expire" behavior, documented in docs/api-contracts/campaign-service.md) - this is
-- purely an observability/admin-review signal, not a state-machine transition.

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS stale_tariff_flag BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS stale_tariff_reason VARCHAR(500);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS stale_tariff_flagged_at TIMESTAMPTZ;

-- Cheap admin-dashboard lookup: "show me every flagged campaign".
CREATE INDEX IF NOT EXISTS idx_campaigns_stale_tariff_flag
    ON campaigns (stale_tariff_flag)
    WHERE stale_tariff_flag = true;
