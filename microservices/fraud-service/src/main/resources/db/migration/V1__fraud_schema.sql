-- fraud-service schema (ADR-006, ADR-016, ADR-029; design-note.md Section 6).
-- MsisdnLifecycleSignal + FraudRule + FraudSignal + FraudCase aggregate family in fraud-db.
-- fraud-service is read-only relative to subscription-service (ADR-029 Section 1): no table here
-- duplicates or reads from subscription-db; the msisdn_lifecycle_signal log is populated only from
-- subscription-service's already-published events consumed via the inbox (Feature 23.2).
-- Platform tables (outbox_event V900, inbox_message V901) are added from classpath:db/migration/platform.

-- Rule catalogue: a small, fixed set of rule codes with admin-tunable thresholds/windows
-- (ADR-029 Section 4 - a parameterized-threshold aggregate, not a boolean-expression rule engine).
CREATE TABLE IF NOT EXISTS fraud_rule (
    code            VARCHAR(40)  PRIMARY KEY
                                 CHECK (code IN ('RAPID_SIM_SWAP', 'MSISDN_CHURN_VELOCITY', 'SUSPEND_REACTIVATE_VELOCITY')),
    window_minutes  INT          NOT NULL CHECK (window_minutes > 0),
    threshold_count INT          NOT NULL CHECK (threshold_count > 0),
    severity        VARCHAR(10)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Raw ingested event log the rolling-window queries run against (pruned periodically). customer_id,
-- msisdn, and subscription_id are nullable because not every consumed event carries all three, and
-- msisdn.released.v1 historically lacked customer_id (ADR-029 Amendment 1 - resolved defensively in 23.2).
CREATE TABLE IF NOT EXISTS msisdn_lifecycle_signal (
    id              UUID         PRIMARY KEY,
    event_type      VARCHAR(30)  NOT NULL
                                 CHECK (event_type IN ('MSISDN_ALLOCATED', 'MSISDN_RELEASED',
                                                       'SUBSCRIPTION_SUSPENDED', 'SUBSCRIPTION_ACTIVATED')),
    customer_id     UUID,
    msisdn          VARCHAR(20),
    subscription_id UUID,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    ingested_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- MSISDN-scoped rolling-window lookback (RAPID_SIM_SWAP: release then re-allocate for the same MSISDN).
CREATE INDEX IF NOT EXISTS idx_msisdn_lifecycle_signal_msisdn_occurred
    ON msisdn_lifecycle_signal (msisdn, occurred_at);
-- customerId-scoped rolling-window lookback (MSISDN_CHURN_VELOCITY: allocate/release cycles per customer).
CREATE INDEX IF NOT EXISTS idx_msisdn_lifecycle_signal_customer_type_occurred
    ON msisdn_lifecycle_signal (customer_id, event_type, occurred_at);
-- subscriptionId-scoped rolling-window lookback (SUSPEND_REACTIVATE_VELOCITY: suspend/activate cycling).
CREATE INDEX IF NOT EXISTS idx_msisdn_lifecycle_signal_subscription_type_occurred
    ON msisdn_lifecycle_signal (subscription_id, event_type, occurred_at);

-- An evaluated rule hit. source_signal_ids references the msisdn_lifecycle_signal rows that triggered it.
CREATE TABLE IF NOT EXISTS fraud_signal (
    id                UUID         PRIMARY KEY,
    rule_code         VARCHAR(40)  NOT NULL REFERENCES fraud_rule (code),
    customer_id       UUID,
    msisdn            VARCHAR(20),
    subscription_id   UUID,
    severity          VARCHAR(10)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    triggered_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    source_signal_ids UUID[]       NOT NULL DEFAULT '{}'
);

-- Per-customer signal history lookups (case escalation, 23.3).
CREATE INDEX IF NOT EXISTS idx_fraud_signal_customer_triggered
    ON fraud_signal (customer_id, triggered_at);
CREATE INDEX IF NOT EXISTS idx_fraud_signal_rule_code
    ON fraud_signal (rule_code);

-- One or more related signals escalated into an actionable case. signal_ids references fraud_signal rows.
CREATE TABLE IF NOT EXISTS fraud_case (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                              CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'CONFIRMED', 'DISMISSED')),
    signal_ids   UUID[]       NOT NULL DEFAULT '{}',
    opened_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    resolved_by  VARCHAR(100)
);

-- Open-case-per-customer lookups (dedupe escalation, 23.3).
CREATE INDEX IF NOT EXISTS idx_fraud_case_customer_status
    ON fraud_case (customer_id, status);
