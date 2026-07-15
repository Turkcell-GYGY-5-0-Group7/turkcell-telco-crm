-- campaign-service schema (Feature 21.2.2, ADR-027 Section 4 ratification amendment).
-- CampaignRedemption.reserve(...) sets reservedUntil to bound a RESERVED hold; a reservation-expiry
-- reaper (Feature 21.4, mirroring subscription-service's MSISDN reaper, starter-lock/ADR-024) queries
-- this column to release RESERVED rows stranded by an abandoned order. Added now (not deferred to
-- 21.4) because it is intrinsic to the CampaignRedemption.reserve(...) domain method shipped in this
-- feature, not to the reaper's own scheduling/locking wiring.

ALTER TABLE campaign_redemptions ADD COLUMN IF NOT EXISTS reserved_until TIMESTAMPTZ;

-- Partial index: the reaper only ever scans still-live RESERVED rows for an elapsed hold.
CREATE INDEX IF NOT EXISTS idx_campaign_redemptions_reserved_until
    ON campaign_redemptions (reserved_until)
    WHERE status = 'RESERVED';
