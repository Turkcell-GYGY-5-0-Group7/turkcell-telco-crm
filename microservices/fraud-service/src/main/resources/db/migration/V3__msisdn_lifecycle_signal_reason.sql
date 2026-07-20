-- Add the suspension reason to the raw event log (ADR-029 Amendment 3, Feature 23.2.4).
-- subscription.suspended.v1 carries a `reason` (e.g. NON_PAYMENT); SUSPEND_REACTIVATE_VELOCITY must
-- EXCLUDE reason=NON_PAYMENT suspensions from its rolling-window transition count to suppress
-- legitimate dunning-cycle false positives. The count reads historical msisdn_lifecycle_signal rows,
-- so the reason must be persisted on the row (not just read off the triggering event). Nullable:
-- only SUBSCRIPTION_SUSPENDED rows populate it; every other event type leaves it NULL.
ALTER TABLE msisdn_lifecycle_signal ADD COLUMN IF NOT EXISTS reason VARCHAR(40);
