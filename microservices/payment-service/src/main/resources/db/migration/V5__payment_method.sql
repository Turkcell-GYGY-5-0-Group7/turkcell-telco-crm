-- Sprint 24 (24.6, FR-25): records how the customer pays (credit card, bank transfer, wallet).
-- Label only in the MVP: the mock PSP ignores the method, and wallet balance modeling is
-- deliberately out of scope (Sprint 24 design-note D6). Existing rows are backfilled via the
-- default (ADR-019 backward-compatible addition).

ALTER TABLE payments ADD COLUMN IF NOT EXISTS method varchar(20) NOT NULL DEFAULT 'CREDIT_CARD';
