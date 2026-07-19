-- Sprint 24 feature 24.5 (FR-03): optional contact info on the customer master record.
-- Stored plain per design-note D5: the PDF mandates encryption at rest only for TCKN and card data;
-- email/phone are masked in logs/telemetry via the platform @Sensitive annotation (ADR-011, ADR-021).

ALTER TABLE customers
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN phone VARCHAR(32)  NULL;
