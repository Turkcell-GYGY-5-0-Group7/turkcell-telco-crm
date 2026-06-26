-- identity-service schema (ADR-006, ADR-016). RBAC projection and audit storage.
-- Credentials and tokens live in Keycloak (ADR-011), so users has no password_hash and there is no
-- refresh_tokens table. Platform tables (outbox V900) are added from classpath:db/migration/platform.

CREATE TABLE IF NOT EXISTS users (
    id           UUID         PRIMARY KEY,
    keycloak_id  VARCHAR(255) NOT NULL UNIQUE,
    username     VARCHAR(255) NOT NULL,
    email        VARCHAR(320) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS roles (
    id    UUID         PRIMARY KEY,
    name  VARCHAR(128) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS permissions (
    id    UUID         PRIMARY KEY,
    code  VARCHAR(128) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id        UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id  UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id         UUID         PRIMARY KEY,
    actor_id   UUID,
    action     VARCHAR(128) NOT NULL,
    entity     VARCHAR(128) NOT NULL,
    entity_id  VARCHAR(255),
    details    JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_id ON audit_log (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
