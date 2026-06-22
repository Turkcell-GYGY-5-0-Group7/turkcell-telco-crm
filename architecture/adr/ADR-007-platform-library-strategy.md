# ADR-007 Platform Library Strategy

Status: Accepted
Date: 2026-06-19

## Context

The Telco CRM platform requires a consistent set of cross-cutting capabilities:

* CQRS + Mediator pipeline
* Exception handling
* API response standardization
* Security context management
* Outbox and Inbox patterns
* Observability (logging, tracing, metrics)
* Event publishing abstractions

These capabilities must be reusable across all microservices without duplicating code.

However, we must avoid:

* Creating a monolithic “common library”
* Tightly coupling services to infrastructure implementations
* Mixing business logic with platform logic

## Decision

A **modular platform architecture** shall be introduced under:

```text id="z8c1m2"
platform/
```

### Design Principles

* Each module is independent
* Each module has a single responsibility
* Modules are composable via Spring Boot starters
* Business logic is strictly forbidden in platform modules

## Platform Module Structure

```text id="p9x2d8"
platform/

├── api/
├── cqrs/
├── mediator/
├── security/
├── exception/
├── inbox/
├── outbox/
├── event-bus/
├── observability/
├── testing/
├── autoconfigure/
├── starter-api/
├── starter-security/
├── starter-outbox/
├── starter-inbox/
├── starter-mediator/
└── starter-observability/
```

## Core Design Rules

### 1. API Standardization

* ApiResult and ApiError are mandatory response wrappers
* All controllers MUST return ApiResult

### 2. CQRS & Mediator

* Commands/Queries/Events are framework-agnostic
* Pipeline behaviors are pluggable

### 3. Outbox & Inbox

* Outbox must support transactional guarantees
* Inbox must enforce idempotency

### 4. Exception Handling

* Global exception mapping provided via autoconfigure module
* Domain exceptions are framework-independent

### 5. Security

* JWT-based user context propagation
* mTLS handled at infrastructure layer, not application layer

### 6. Observability

* Correlation ID propagation required
* OpenTelemetry integration mandatory

## Dependency Rule

Microservices MAY ONLY depend on:

* platform starters
* NOT internal platform modules directly

Example:

```xml id="x2m9q1"
starter-outbox
starter-mediator
starter-security
```

## Extension Rule

New platform capabilities MUST be added as:

* new module
* not modification of existing modules

## Consequences

### Positive

* Strong standardization across services
* High reuse of infrastructure logic
* AI-friendly predictable patterns
* Faster service creation

### Negative

* Requires strong governance
* Risk of over-engineering if misused

## Alternatives Considered

### Single common library

Rejected due to tight coupling and low modularity.

### Per-service implementation

Rejected due to duplication and inconsistency.

## Related ADRs

* ADR-004 Architecture Style
* ADR-008 CQRS & Mediator Strategy
