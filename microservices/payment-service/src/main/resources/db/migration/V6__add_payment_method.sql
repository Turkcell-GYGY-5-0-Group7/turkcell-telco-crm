-- FR-25: capture the settlement method. Existing rows were all charged through the mock
-- card PSP, so CREDIT_CARD is the correct backfill value.
ALTER TABLE payments
    ADD COLUMN payment_method VARCHAR(32) NOT NULL DEFAULT 'CREDIT_CARD';

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_method
        CHECK (payment_method IN ('CREDIT_CARD', 'BANK_TRANSFER', 'WALLET'));
