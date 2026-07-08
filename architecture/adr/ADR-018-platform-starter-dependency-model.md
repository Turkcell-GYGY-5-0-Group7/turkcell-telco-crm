# ADR-018 Platform Starter Dependency Model

Status: Accepted
Date: 2026-06-19

---

## Context

The platform provides shared capabilities (CQRS, security, outbox, observability, etc.).

We must define how these capabilities are consumed by microservices without:

* Tight coupling
* Manual configuration duplication
* Infrastructure leakage into business code

---

## Decision

We adopt a **Spring Boot Starter-based modular dependency model**.

---

# 1. Starter-Based Architecture

Each platform capability is exposed as a:

```text id="p1k9lm"
spring-boot-starter-*
```

Examples:

* starter-api
* starter-security
* starter-mediator
* starter-outbox
* starter-inbox
* starter-observability

---

# 2. Dependency Rule

Microservices MUST ONLY depend on starters.

They MUST NOT depend on internal platform modules directly.

---

# 3. Auto-Configuration Rule

Each starter MUST provide:

* AutoConfiguration classes
* Conditional beans
* Default implementations
* Override capability

---

# 4. Composition Rule

Services compose behavior via dependency inclusion:

```xml id="d1k9lm"
starter-security
starter-outbox
starter-mediator
```

---

# 5. Override Rule

Services MAY override:

* beans
* configurations
* behaviors

without modifying platform code.

---

## Consequences

### Positive

* Clean separation between platform and services
* High reusability
* Easy service bootstrap
* Strong modular design

### Negative

* Requires disciplined starter design
* Debugging auto-config issues can be complex

---

## Related ADRs

* ADR-007 Platform Library Strategy
* ADR-014 CI/CD Strategy

---

## Amendment (2026-07-08)

Status: Accepted (amendment). Raised during Feature 14.5 (Avro Schema Governance Reconciliation,
`docs/tasks/sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md`), where
code-review flagged that `platform-event-contracts` had been added as a direct, non-starter
dependency to 6 additional services (test-scope) and asked tech-lead to rule on whether this
violates Section 2's unscoped "MUST ONLY depend on starters" text. Ruled on by tech-lead. Does not
supersede or rewrite the Decision above; adds one explicit, bounded carve-out so this question is
not re-litigated by a future agent.

### A1. The Dependency Rule targets runtime infrastructure coupling, not contract-definition modules

Section 2's "Microservices MUST ONLY depend on starters... MUST NOT depend on internal platform
modules directly" exists to prevent the three harms named in this ADR's own Context: tight coupling
to platform internals, manual configuration duplication, and infrastructure leakage into business
code. Those harms are properties of modules like `platform-core` that carry business logic, bean
wiring, `AutoConfiguration`, and behavior a service would otherwise have to reimplement or
configure by hand. `platform-event-contracts` has none of that shape: it contains only Avro
schema definitions and their generated Java data-carrier classes (`SpecificRecord` POJOs) plus,
as of Feature 14.5, a small test-support helper packaged as a test-jar. It has zero
`AutoConfiguration` classes, injects no runtime behavior into a consuming service's Spring context,
and could not leak infrastructure into business code even in principle - depending on it is
equivalent in kind to depending on a generated-DTO/protobuf-stubs module, not to depending on
`platform-core`.

### A2. Ruling: this dependency pattern is compliant, with a stated scope

Direct (non-starter) dependency on `platform-event-contracts` - compile-scope where a service
constructs or deserializes the canonical event types at runtime, test-scope (plus its `test-jar`
classifier) where a service's contract tests assert against the canonical schema via
`AvroContractAssertions` - is **compliant** with this ADR's Dependency Rule, not an exception to
it. This carve-out is scoped to `platform-event-contracts` specifically (a pure schema/contract-
definitions module, no `AutoConfiguration`, no runtime wiring). It does not extend to any other
module under `platform/` that is not itself a `spring-boot-starter-*` - `platform-core`,
`platform-autoconfigure`, and similar internal modules remain fully subject to the unscoped
Dependency Rule and MUST continue to be consumed only through a starter.

### A3. Basis for the ruling - not a new pattern, an existing one confirmed

This is not a new allowance invented for Feature 14.5. Four services (`usage-service`,
`billing-service`, `notification-service`, `ticket-service`) already depended on
`platform-event-contracts` directly before this feature - two of them (`billing-service`,
`usage-service`) at compile scope, i.e. in shipped production code, not merely in tests - and this
had never been flagged as an ADR-018 violation through however many prior review passes those
services went through. Feature 14.5 (phase 6, event-integration) extended the identical pattern,
in test scope only, to 6 more services so every producing service's contract test could load the
one real canonical schema instead of a hand-maintained local copy. Ratifying the existing,
already-shipped pattern explicitly - rather than leaving it as one agent's unescalated reading of
an absolute sentence - is what this amendment closes.

### A4. What would make this a real violation

If `platform-event-contracts` ever gains an `AutoConfiguration` class, a Spring bean that performs
I/O or business logic, or any other form of injected runtime behavior, it stops qualifying for this
carve-out at that point and must be re-evaluated - either split into a schema-only module plus a
proper starter, or re-escalated to tech-lead. This amendment covers the module as it exists today
(schemas, generated data classes, test-support assertions only), not whatever it might become.
