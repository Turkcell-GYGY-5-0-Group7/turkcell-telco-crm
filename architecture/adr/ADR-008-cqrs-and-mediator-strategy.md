# ADR-008 CQRS & Mediator Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

The platform uses CQRS and Mediator as a core application pattern inside domain-heavy services.

We are building a **custom mediator and CQRS framework** under the `platform/mediator` and `platform/cqrs` modules.

We must define:

* How commands, queries, and events are structured
* How handlers are executed
* How pipeline behaviors work
* How transactions are managed
* How validation, logging, and security are applied consistently
* How async and sync flows integrate with event-driven architecture

We must avoid coupling application logic to Spring internals.

---

## Decision

We will implement a **custom Mediator-based CQRS system** with pluggable pipeline behaviors.

---

# 1. Core CQRS Model

## Command

Represents a state-changing operation.

```text id="a1c2qz"
Command → CommandHandler → Result
```

Rules:

* MUST NOT return domain entities directly
* MUST be immutable
* MUST represent intent (not data access)

---

## Query

Represents a read-only operation.

```text id="b2d9lm"
Query → QueryHandler → DTO
```

Rules:

* MUST NOT modify state
* MAY bypass domain layer for performance (read models allowed)
* SHOULD return optimized DTOs

---

## Event

Represents something that already happened.

```text id="c3k8qp"
Event → EventHandler
```

Rules:

* MUST be immutable
* MUST be versioned (`*.v1`)
* MUST be publishable to Kafka via Outbox

---

# 2. Mediator Execution Model

All requests go through a single entry point:

```text id="m1n9qk"
Mediator.send(request)
```

Flow:

```text id="m2p8lm"
Request
  → Pipeline Behaviors
  → Handler Resolution
  → Handler Execution
  → Response
```

---

# 3. Pipeline Behaviors (Cross-Cutting Concerns)

All requests pass through ordered pipeline behaviors:

### Mandatory behaviors:

1. ValidationBehavior
2. SecurityBehavior
3. LoggingBehavior
4. TransactionBehavior (for commands)
5. PerformanceBehavior

---

Example flow:

```text id="p9x1lm"
Request
 → Validation
 → Authorization
 → Logging
 → Transaction
 → Handler
```

---

# 4. Transaction Rules

* Commands MUST run inside transactional behavior
* Queries MUST NOT start transactions
* Event publishing MUST NOT block transaction commit

---

# 5. Handler Rules

* One handler per command/query/event
* Handlers MUST be stateless
* Handlers MUST NOT call other handlers directly
* Cross-handling MUST be done via domain services or events

---

# 6. Integration with Domain Layer

* Handlers orchestrate use cases
* Domain contains business rules
* Application layer contains workflow logic

---

# 7. Async Event Integration

* Domain events are converted into integration events
* Integration events are published via Outbox pattern
* Handlers MUST NOT publish directly to Kafka

---

# 8. Spring Integration Rule

* Spring is used ONLY for wiring (dependency injection)
* Business logic MUST NOT depend on Spring annotations

---

## Consequences

### Positive

* Highly structured application flow
* Fully testable request pipeline
* Clear separation of concerns
* AI-friendly execution model
* Easy to extend with new behaviors

### Negative

* Requires disciplined handler design
* Slight overhead for simple operations
* Learning curve for new developers

---

## Alternatives Considered

### Direct service calls (no mediator)

Rejected due to lack of structure and cross-cutting control.

### Using third-party CQRS libraries

Rejected to maintain full control and customization.

---

## Related ADRs

* ADR-004 Architecture Style
* ADR-007 Platform Library Strategy
* ADR-009 Event Driven Architecture
