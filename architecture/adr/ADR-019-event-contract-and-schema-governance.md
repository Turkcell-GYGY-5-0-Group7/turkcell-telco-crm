# ADR-019 Event Contract & Schema Governance

Status: Accepted
Date: 2026-06-19

---

## Context

The platform uses Kafka with Avro-based event contracts.

We need strict governance to ensure:

* backward compatibility
* schema evolution safety
* multi-service event interoperability
* prevention of breaking changes
* versioned event history

---

## Decision

We adopt a **centralized schema governance model using Avro + Schema Registry**.

---

# 1. Event Naming Convention

All events MUST follow:

```text id="e1k9lm"
<domain>.<event>.<version>
```

Example:

* customer.created.v1
* order.paid.v1

---

# 2. Schema Registry Rule

All schemas MUST be registered before deployment.

No unregistered schema is allowed in production.

---

# 3. Compatibility Rule

Schemas MUST be:

* backward compatible (minimum requirement)
* forward compatible when possible

---

# 4. Versioning Rule

* Breaking changes require new version (v2, v3, ...)
* Old versions MUST remain supported until deprecated

---

# 5. Ownership Rule

Each service owns its event schemas.

Shared event contracts exist only in:

```text 
id="platform/event-contracts"
```

---

# 6. Event Immutability Rule

Once published:

* events MUST NOT change
* events are immutable facts

---

## Consequences

### Positive

* Safe event evolution
* Strong interoperability
* Reliable distributed systems
* Audit-friendly architecture

### Negative

* Schema governance overhead
* Requires discipline in versioning

---

## Related ADRs

* ADR-009 Event Driven Architecture
* ADR-005 Service Communication Strategy

---

## Amendment (2026-07-07)

Status: Accepted (amendment). Raised and ruled on by tech-lead during Feature 14.5 (Avro Schema
Governance Reconciliation, `docs/tasks/sprint-14-testing-and-hardening/`). Does not supersede or
rewrite the Decision above; clarifies scope and closes a real drift found in the running system.

### A1. Governance applies to SHAPE, not to literal Avro binary wire bytes

This ADR's Schema Registry Rule, Compatibility Rule, and Versioning Rule are enforced over the
**JSON-serialized shape** each `.avsc` schema describes (field names, types, nullability, and
evolution rules), not over literal Avro binary-encoded bytes on the Kafka wire. A schema is
"canonical" for an event when it accurately and provably describes that event's real JSON payload
shape - not when every producer/consumer is forced onto Avro binary (de)serialization at runtime.
Services are NOT required to adopt Avro binary encoding to be ADR-019-compliant.

### A2. The outbox continues to publish plain JSON (no change to ADR-009)

Per ADR-009, the transactional outbox publishes plain JSON, verified separately and working. This
amendment does not change that, does not require an encoding migration, and does not put Avro
binary (de)serialization on any team's roadmap. Nothing here overrides ADR-009.

### A3. `.avsc` files remain the single canonical source of truth for event SHAPE

`platform/platform-event-contracts/src/main/avro/` remains the one and only place an event's
contract is authored and reviewed - field names, types, nullability, and evolution rules continue
to follow this ADR's existing Compatibility Rule and Versioning Rule unchanged. What changes is how
conformance is proven: each canonical `.avsc` is validated by schema-compatibility test tooling
(per-service contract tests, e.g. the existing `*EventContractTest` pattern such as
`NotificationEventContractTest`) that assert the schema's field set against the producing service's
real, hand-written event-payload record (POJO/record class serialized to JSON) - not by literal
Avro binary serialization at runtime, and not by trust that a schema file merely exists.

### A4. Concrete finding that prompted this amendment

No tooling ever connected the canonical `.avsc` files to the real JSON emitted by each service, so
drift accumulated silently. Verified this session by cross-referencing every `outboxService.publish(
..., "<event>.v1", ...)` call site in `microservices/**/src/main/java` against
`platform/platform-event-contracts/src/main/avro/`:

* **14 real, production-emitted event types have no canonical schema at all**: `order.cancelled.v1`,
  `payment.failed.v1`, `payment.refunded.v1`, `tariff.created.v1`, `tariff.price-changed.v1`,
  `ticket.opened.v1`, `ticket.assigned.v1`, `ticket.resolved.v1`, `ticket.sla-breached.v1`,
  `invoice.paid.v1`, `invoice.overdue.v1`, `notification.dispatched.v1`, `user.created.v1`, and
  `user.deleted.v1`. Twelve of these already have an ad hoc, per-service snapshot living under that
  service's own `src/test/resources/avro/` (evidence the shape was captured once for a test, then
  never promoted to the canonical directory); the remaining two (`user.created.v1`,
  `user.deleted.v1`, identity-service) have no schema anywhere and are also absent from
  `docs/architecture/event-catalog.md`'s event registry. (This count was independently re-verified
  during this ruling and is one higher than first estimated - see
  `docs/tasks/sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md` for the full,
  itemized list and the verification method; that document, not a remembered headline number, is
  authoritative on the exact count and item list.)
* Both gaps are closed under Feature 14.5, tracked in `docs/tasks/sprint-14-testing-and-hardening/
  14.5-avro-schema-governance-ruling.md`.

### A5. File-naming convention for `platform/platform-event-contracts/src/main/avro/`

Files in this directory MUST be named in kebab-case matching the `domain-event.avsc` pattern (for
example `customer-registered.avsc`, `subscription-activated.avsc`), independent of the Avro
record's own `"name"` field, which stays PascalCase (for example `EventEnvelope`,
`CustomerRegisteredV1`) and continues to drive generated-Java-class naming unchanged. Existing
`EventEnvelope.avsc` is non-compliant with this convention and is being renamed to
`event-envelope.avsc` under Feature 14.5 (record `name` remains `EventEnvelope`; the
`avro-maven-plugin` string-replacement property in `platform/platform-event-contracts/pom.xml` that
references the file by its old name must be updated in the same change).
