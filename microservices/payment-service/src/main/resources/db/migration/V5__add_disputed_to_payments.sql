-- Adds the disputed flag to payments (dispute-service extension, ADR-028 Section 5). Suppresses
-- retry/settlement while a payment is under an open dispute (dispute.opened.v1 /
-- dispute.resolved-merchant.v1). This is payment-service's own migration, under its own write
-- ownership - dispute-service never writes to payment-db directly.

ALTER TABLE payments ADD COLUMN IF NOT EXISTS disputed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_payments_disputed ON payments (disputed);
