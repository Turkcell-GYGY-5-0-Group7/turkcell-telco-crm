# ADR-016 Database Migration Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

Each microservice owns its own database (`<service>-db`) using PostgreSQL.

We require a standardized migration strategy to ensure:

* Safe schema evolution
* Zero-downtime deployments
* Repeatable deployments across environments
* Compatibility with CI/CD pipelines
* Independent service evolution

---

## Decision

We adopt **Flyway as the single database migration tool**.

---

# 1. Ownership Rule

Each microservice MUST own its migrations.

```text id="m1k9lm"
customer-service → customer-db → Flyway migrations
```

No shared migration files across services.

---

# 2. Migration Structure

```text id="m2k9lm"
resources/db/migration/

V1__init.sql
V2__add_index.sql
V3__add_customer_status.sql
```

---

# 3. Migration Rules

* Migrations are immutable (never modify after merge)
* Only forward migrations allowed
* Rollbacks are handled via new migrations

---

# 4. CI Enforcement

* Flyway validation runs in CI
* Schema drift detection is mandatory

---

# 5. Environment Consistency

Same migrations MUST run in:

* local
* test
* staging
* production

No environment-specific SQL allowed.

---

## Consequences

### Positive

* Predictable database evolution
* Strong version control for schemas
* CI-safe schema management

### Negative

* Requires discipline in schema design
* No easy rollback (forward-only model)

---

## Related ADRs

* ADR-006 Database Strategy
