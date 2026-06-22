# ADR-013 Testing Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform consists of multiple microservices using:

* Clean Architecture
* CQRS + Mediator
* Event-driven communication (Kafka + Outbox + Inbox)
* Distributed transactions (eventual consistency)
* Multiple databases per service

This introduces complexity in ensuring:

* Business correctness
* Integration reliability
* Event consistency
* Cross-service behavior validation

A standardized testing strategy is required across all services.

---

## Decision

We adopt a **multi-layer testing strategy**:

---

# 1. Test Pyramid Structure

```text id="t1k9lm"
Unit Tests
Integration Tests
Contract Tests
End-to-End Tests
```

---

# 2. Unit Testing (Mandatory)

### Scope:

* Domain layer
* Application layer (CQRS handlers)
* Business rules
* Value objects

### Rules:

* No Spring context required
* No database required
* Must be fast (< 50ms per test)

---

# 3. Integration Testing

### Scope:

* Repository layer
* Database interactions
* Kafka producers/consumers
* Outbox & Inbox behavior

### Tools:

* Testcontainers (PostgreSQL, Kafka, Redis)

---

# 4. Contract Testing

### Scope:

* REST API contracts
* gRPC contracts
* Kafka event schemas (Avro)

### Rules:

* Events MUST validate against schema registry
* API changes MUST be backward compatible

---

# 5. End-to-End Testing

### Scope:

* Full system flows
* BFF → Gateway → Service → Kafka → Consumer chain

### Usage:

* Limited and critical flows only (billing, order, payment)

---

# 6. Kafka Testing Strategy

* Embedded Kafka or Testcontainers Kafka
* Event publishing MUST be verified via outbox
* Inbox idempotency MUST be tested

---

# 7. Test Data Strategy

* Each test MUST be self-contained
* No shared test state
* Use factory-based test data builders

---

# 8. CI Enforcement Rule

* Unit tests are mandatory for every commit
* Integration tests required for merge
* E2E tests run on release pipelines only

---

## Consequences

### Positive

* High confidence in system correctness
* Safe refactoring across microservices
* Reliable event-driven validation
* AI-generated code can be validated automatically

### Negative

* Increased test maintenance overhead
* Slower CI pipelines
* Requires discipline in test design

---

## Alternatives Considered

### Unit tests only

Rejected due to distributed system complexity.

### Full E2E testing only

Rejected due to slow feedback cycles.

---

## Related ADRs

* ADR-009 Event Driven Architecture
* ADR-005 Service Communication Strategy
* ADR-008 CQRS & Mediator Strategy
