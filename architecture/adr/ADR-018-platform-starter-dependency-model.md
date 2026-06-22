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
