-- payment_attempts table: per-charge attempt audit trail (ADR-006, ADR-016).

CREATE TABLE IF NOT EXISTS payment_attempts (
    id              UUID        PRIMARY KEY,
    payment_id      UUID        NOT NULL REFERENCES payments (id),
    attempt_number  INT         NOT NULL,
    status          VARCHAR(32) NOT NULL,
    error_message   TEXT,
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment_id ON payment_attempts (payment_id);
