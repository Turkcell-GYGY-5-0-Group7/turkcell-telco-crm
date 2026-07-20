-- Fraud-review SLA policies (Sprint 23 Feature 23.4.2).
--
-- ticket-service auto-opens a ticket when it consumes fraud.case-opened.v1 (fraud-service, ADR-029
-- Section 5 - detect-and-alert only, no automated suspension). The external_ref column and its
-- index already ship in V2__add_external_ref_and_dispute_sla.sql (Sprint 22); this migration was
-- originally authored as a second V2 on the sprint-23 branch and collided with it at merge -
-- renumbered to V3 and reduced to the fraud-specific rows only. ON CONFLICT keeps it safe on
-- databases that already ran the original sprint-23 file.
--
-- SLA policies for the FRAUD_REVIEW category so auto-opened fraud tickets are assigned to the
-- fraud-ops queue via the existing OpenTicketCommandHandler/SlaPolicy path (not the customer-care
-- fallback). Priority mirrors the fraud case's highest signal severity (HIGH/MEDIUM/LOW).
INSERT INTO sla_policies (category, priority, team, resolution_minutes) VALUES
    ('FRAUD_REVIEW', 'HIGH',   'fraud-ops',  120),
    ('FRAUD_REVIEW', 'MEDIUM', 'fraud-ops',  480),
    ('FRAUD_REVIEW', 'LOW',    'fraud-ops', 1440)
ON CONFLICT (category, priority) DO NOTHING;
