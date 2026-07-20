-- Dispute-service extension (ADR-028 Section 3): a hold flag on invoices and a line-type
-- discriminator on invoice_lines so a dispute credit line is distinguishable from ordinary lines.
-- This is billing-service's own migration, under its own write ownership - dispute-service never
-- writes to billing-db directly.

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS dispute_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS line_type VARCHAR(16) NOT NULL DEFAULT 'RECURRING';

CREATE INDEX IF NOT EXISTS idx_invoices_dispute_status ON invoices (dispute_status);
