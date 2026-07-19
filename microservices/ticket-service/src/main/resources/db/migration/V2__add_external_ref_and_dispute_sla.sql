-- Dispute-service integration (Sprint 22 Feature 22.6.2, reuse-before-build): correlates a
-- DISPUTE-category ticket back to its originating dispute, and gives disputes their own SLA
-- policy row instead of silently falling back to the generic customer-care/24h default.

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS external_ref VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_tickets_external_ref ON tickets(external_ref);

INSERT INTO sla_policies (category, priority, team, resolution_minutes) VALUES
    ('DISPUTE', 'HIGH',   'billing-support', 240),
    ('DISPUTE', 'MEDIUM', 'billing-support', 480),
    ('DISPUTE', 'LOW',    'billing-support', 1440)
ON CONFLICT (category, priority) DO NOTHING;
