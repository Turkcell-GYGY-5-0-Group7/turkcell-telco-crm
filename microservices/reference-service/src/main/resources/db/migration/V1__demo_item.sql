-- Reference service schema. Platform tables (outbox V900, inbox V901) are added from
-- classpath:db/migration/platform via the configured Flyway locations.

CREATE TABLE IF NOT EXISTS demo_item (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(280) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
