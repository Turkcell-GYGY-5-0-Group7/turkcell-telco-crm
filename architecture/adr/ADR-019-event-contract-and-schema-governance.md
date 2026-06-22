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
