-- Fraud-review SLA (Sprint 23 Feature 23.4.2)
--
-- ticket-service auto-opens a ticket when it consumes fraud.case-opened.v1 (fraud-service, ADR-029
-- Section 5 - detect-and-alert only, no automated suspension). external_ref carries the originating
-- fraud caseId so an agent (and the GET /api/v1/tickets/{id} response) can trace the ticket back to
-- the fraud case. The generic external_ref column and its index already exist
-- (V2__add_external_ref_and_dispute_sla.sql, Sprint 22) - this migration only adds the SLA policies
-- for the new FRAUD_REVIEW category.

-- SLA policies for the FRAUD_REVIEW category so auto-opened fraud tickets are assigned to the
-- fraud-ops queue via the existing OpenTicketCommandHandler/SlaPolicy path (not the customer-care
-- fallback). Priority mirrors the fraud case's highest signal severity (HIGH/MEDIUM/LOW).
INSERT INTO sla_policies (category, priority, team, resolution_minutes) VALUES
    ('FRAUD_REVIEW', 'HIGH',   'fraud-ops',  120),
    ('FRAUD_REVIEW', 'MEDIUM', 'fraud-ops',  480),
    ('FRAUD_REVIEW', 'LOW',    'fraud-ops', 1440)
ON CONFLICT (category, priority) DO NOTHING;
