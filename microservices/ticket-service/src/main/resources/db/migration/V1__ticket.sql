-- Ticket service schema (feature 12.4.2)

CREATE TABLE sla_policies (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category      VARCHAR(100) NOT NULL,
    priority      VARCHAR(50)  NOT NULL,
    team          VARCHAR(100) NOT NULL,
    resolution_minutes INT     NOT NULL,
    UNIQUE (category, priority)
);

CREATE TABLE tickets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL,
    category      VARCHAR(100) NOT NULL,
    priority      VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    assigned_team VARCHAR(100),
    subject       VARCHAR(500) NOT NULL,
    sla_due_at    TIMESTAMPTZ,
    sla_breached_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMPTZ
);

CREATE INDEX idx_tickets_customer_id  ON tickets(customer_id);
CREATE INDEX idx_tickets_status       ON tickets(status);
CREATE INDEX idx_tickets_sla_due_at   ON tickets(sla_due_at) WHERE status != 'RESOLVED';

CREATE TABLE ticket_comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id  UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id  UUID         NOT NULL,
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_comments_ticket_id ON ticket_comments(ticket_id);

-- Platform outbox (outbox_event) and inbox (inbox_message) tables are provided by
-- starter-outbox (V900) and starter-inbox (V901) via classpath:db/migration/platform (ADR-016, ADR-019).

-- Seed default SLA policies (FR-32)
INSERT INTO sla_policies (category, priority, team, resolution_minutes) VALUES
    ('BILLING',      'HIGH',   'billing-support',   240),
    ('BILLING',      'MEDIUM', 'billing-support',   480),
    ('BILLING',      'LOW',    'billing-support',  1440),
    ('TECHNICAL',    'HIGH',   'tech-support',      120),
    ('TECHNICAL',    'MEDIUM', 'tech-support',      480),
    ('TECHNICAL',    'LOW',    'tech-support',     1440),
    ('GENERAL',      'HIGH',   'customer-care',     480),
    ('GENERAL',      'MEDIUM', 'customer-care',    1440),
    ('GENERAL',      'LOW',    'customer-care',    2880);
