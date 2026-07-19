-- dispute-service system of record (ADR-028 Section 3). New, independently owned database -
-- dispute-service never gains access to billing-db or payment-db (ADR-006 cross-service data rule).

CREATE TABLE IF NOT EXISTS disputes (
    id                 UUID          PRIMARY KEY,
    invoice_id         UUID,
    payment_id         UUID,
    customer_id        UUID          NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    reason_code        VARCHAR(64)   NOT NULL,
    disputed_amount    NUMERIC(19,2) NOT NULL,
    resolution_amount  NUMERIC(19,2),
    opened_at          TIMESTAMPTZ   NOT NULL,
    resolved_at        TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_disputes_customer_id ON disputes (customer_id);
CREATE INDEX IF NOT EXISTS idx_disputes_invoice_id ON disputes (invoice_id);
CREATE INDEX IF NOT EXISTS idx_disputes_payment_id ON disputes (payment_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON disputes (status);

CREATE TABLE IF NOT EXISTS dispute_evidence (
    id             UUID        PRIMARY KEY,
    dispute_id     UUID        NOT NULL REFERENCES disputes (id),
    submitted_by   VARCHAR(255) NOT NULL,
    object_ref     VARCHAR(500) NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dispute_evidence_dispute_id ON dispute_evidence (dispute_id);

CREATE TABLE IF NOT EXISTS dispute_state_history (
    id           UUID        PRIMARY KEY,
    dispute_id   UUID        NOT NULL REFERENCES disputes (id),
    from_status  VARCHAR(32),
    to_status    VARCHAR(32) NOT NULL,
    changed_by   VARCHAR(255),
    changed_at   TIMESTAMPTZ NOT NULL,
    note         VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_dispute_state_history_dispute_id ON dispute_state_history (dispute_id);
