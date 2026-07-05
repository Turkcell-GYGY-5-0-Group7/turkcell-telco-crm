-- Adds the optional invoice association to payments (ADR-019 backward-compatible addition).
-- Enables invoice-driven charges (POST /api/v1/payments with invoiceId) to carry the invoice
-- identity through to payment.completed.v1 / payment.failed.v1, so billing-service's
-- PaymentCompletedBillingConsumer can mark the invoice paid (Section 14.2).

ALTER TABLE payments ADD COLUMN IF NOT EXISTS invoice_id UUID;

CREATE INDEX IF NOT EXISTS idx_payments_invoice_id ON payments (invoice_id);
