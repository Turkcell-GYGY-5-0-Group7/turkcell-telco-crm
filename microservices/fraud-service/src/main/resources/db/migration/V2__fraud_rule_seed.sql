-- Default FraudRule seed (ADR-029 Section 4 / design-note.md Section 4).
-- These three rule CODES are fixed at this MVP stage; the window/threshold values are admin-tunable
-- at runtime via 23.3's rule-config API without a redeploy. ON CONFLICT keeps re-runs idempotent and
-- deliberately does NOT overwrite operator-tuned values on an already-seeded database.
INSERT INTO fraud_rule (code, window_minutes, threshold_count, severity, enabled) VALUES
    ('RAPID_SIM_SWAP',              15,   1, 'HIGH',   TRUE),
    ('MSISDN_CHURN_VELOCITY',       1440, 3, 'MEDIUM', TRUE),
    ('SUSPEND_REACTIVATE_VELOCITY', 60,   2, 'LOW',    TRUE)
ON CONFLICT (code) DO NOTHING;
