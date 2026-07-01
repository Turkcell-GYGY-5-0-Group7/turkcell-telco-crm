-- billing-service schema (ADR-006, ADR-016).
-- Platform tables (outbox/inbox V900) added from classpath:db/migration/platform.

-- Billing read models — populated from events; the bill-run reads these without cross-service calls.
CREATE TABLE IF NOT EXISTS subscriber_billing_records (
    id                  UUID            PRIMARY KEY,
    subscription_id     UUID            NOT NULL UNIQUE,
    customer_id         UUID            NOT NULL,
    tariff_code         VARCHAR(64)     NOT NULL,
    status              VARCHAR(32)     NOT NULL,  -- ACTIVE, SUSPENDED, TERMINATED
    activated_at        TIMESTAMPTZ     NOT NULL,
    suspended_at        TIMESTAMPTZ,
    terminated_at       TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sbr_customer_id ON subscriber_billing_records (customer_id);
CREATE INDEX IF NOT EXISTS idx_sbr_status ON subscriber_billing_records (status);

CREATE TABLE IF NOT EXISTS tariff_prices (
    id              UUID            PRIMARY KEY,
    tariff_code     VARCHAR(64)     NOT NULL UNIQUE,
    monthly_fee     NUMERIC(19, 4)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL DEFAULT 'TRY',
    effective_from  TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS overage_records (
    id                      UUID            PRIMARY KEY,
    subscription_id         UUID            NOT NULL,
    period_start            TIMESTAMPTZ     NOT NULL,
    period_end              TIMESTAMPTZ     NOT NULL,
    voice_overage_seconds   BIGINT          NOT NULL DEFAULT 0,
    sms_overage_count       BIGINT          NOT NULL DEFAULT 0,
    data_overage_kb         BIGINT          NOT NULL DEFAULT 0,
    aggregated_at           TIMESTAMPTZ     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_overage_sub_period
    ON overage_records (subscription_id, period_start);

-- Core billing domain.
CREATE TABLE IF NOT EXISTS invoices (
    id              UUID            PRIMARY KEY,
    customer_id     UUID            NOT NULL,
    subscription_id UUID            NOT NULL,
    period_start    TIMESTAMPTZ     NOT NULL,
    period_end      TIMESTAMPTZ     NOT NULL,
    sub_total       NUMERIC(19, 4)  NOT NULL,
    tax             NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    grand_total     NUMERIC(19, 4)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL DEFAULT 'TRY',
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT', -- DRAFT, ISSUED, PAID, OVERDUE
    due_date        DATE            NOT NULL,
    issued_at       TIMESTAMPTZ,
    pdf_ref         VARCHAR(512),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_invoices_sub_period
    ON invoices (subscription_id, period_start);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id  ON invoices (customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status        ON invoices (status);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date      ON invoices (due_date);

CREATE TABLE IF NOT EXISTS invoice_lines (
    id          UUID            PRIMARY KEY,
    invoice_id  UUID            NOT NULL REFERENCES invoices(id),
    description VARCHAR(256)    NOT NULL,
    quantity    NUMERIC(10, 4)  NOT NULL,
    unit_price  NUMERIC(19, 4)  NOT NULL,
    line_total  NUMERIC(19, 4)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice_id ON invoice_lines (invoice_id);

CREATE TABLE IF NOT EXISTS bill_cycles (
    id              UUID        PRIMARY KEY,
    customer_id     UUID        NOT NULL UNIQUE,
    day_of_month    INTEGER     NOT NULL DEFAULT 1,
    next_run_date   DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
