# ADR-004 Architecture Style (Hybrid Application Architecture)

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform consists of multiple microservices with varying levels of complexity:

* Some services are simple CRUD-oriented (e.g., reference data, configuration, lightweight entities)
* Some services contain medium complexity business logic (e.g., customer, product, subscription)
* Some services contain complex workflows and event-driven orchestration (e.g., billing, order, payment, provisioning)

A single rigid architecture style (e.g., pure CQRS or pure layered architecture) leads to:

* Over-engineering simple services
* Under-structuring complex services
* Reduced development speed
* Increased cognitive overhead for developers and AI-assisted generation

Therefore, a flexible but governed architecture model is required.

---

## Decision

All microservices MUST explicitly choose one of the following **application architecture modes**:

---

# 1. SIMPLE SERVICE LAYER (CRUD MODE)

### Structure

```text id="a1c9qk"
Controller → Service → Repository
```

### Usage Conditions

This mode MUST be used when:

* Service is primarily CRUD-based
* Business logic is minimal or trivial
* No complex workflows exist
* No event orchestration is required
* No cross-aggregate transactions exist

### Example

* Country Service
* Configuration Service
* Lookup Tables
* Static reference data services

### Rules

* No CQRS required
* No Mediator required
* Direct service invocation allowed
* Domain layer optional (can be thin)

---

# 2. CQRS + MEDIATOR MODE (DEFAULT DOMAIN MODE)

### Structure

```text id="b2k8lm"
Controller → Mediator → Command/Query Handler → Domain → Repository
```

### Usage Conditions

This mode MUST be used when:

* Business rules exist
* Domain logic is non-trivial
* Events are emitted
* Transactions must be controlled
* Read/write separation improves clarity
* Pipelines (validation, logging, security) are needed

### Components

* Commands / Queries
* CommandHandler / QueryHandler
* Pipeline Behaviors
* Domain Aggregates

### Example Services

* Customer Service
* Product Catalog Service
* Subscription Service

### Rules

* All business operations MUST go through Mediator
* Controllers MUST NOT contain business logic
* Domain logic MUST remain framework-independent
* Cross-cutting concerns MUST be handled via pipeline behaviors

---

# 3. DOMAIN ORCHESTRATION MODE (ADVANCED WORKFLOW MODE)

### Structure

```text id="c3m9pq"
Controller → Mediator → Application Service → Domain Services → Aggregates
```

### Usage Conditions

This mode MUST be used when:

* Multiple aggregates are involved in a single workflow
* Complex business orchestration exists
* Saga-like processes are required
* Event-driven coordination is required
* Compensation logic may exist

### Example Services

* Billing Service
* Payment Service
* Order Fulfillment Service
* Provisioning Service

### Rules

* Application services coordinate domain services
* Domain services encapsulate reusable business logic
* Aggregates remain transactional boundaries
* Events MUST be emitted for state transitions

---

## Architectural Decision Rule

Each microservice MUST declare its architecture mode explicitly in its `README.md`:

```text id="d8x2lm"
Architecture Mode: SIMPLE SERVICE LAYER
```

or

```text id="e9k3np"
Architecture Mode: CQRS + MEDIATOR
```

or

```text id="f1m7qz"
Architecture Mode: DOMAIN ORCHESTRATION
```

---

## Dependency Rules (Universal)

Regardless of mode:

### Always allowed

* Domain → no dependencies on framework
* Application → domain only
* Infrastructure → application/domain only
* Presentation → application only

### Never allowed

* Domain depending on Spring / Kafka / JPA
* Controllers containing business logic
* Infrastructure leaking into domain

---

## CQRS Rule Clarification

CQRS is NOT mandatory for all services.

It is a **tool used inside Mode 2 and Mode 3 only**.

---

## Consequences

### Positive

* Prevents over-engineering simple services
* Enables fast development for CRUD services
* Provides strong structure for complex domains
* AI can select correct architecture mode
* Supports scalability without rigidity

### Negative

* Requires discipline in service classification
* Developers must understand when to apply each mode
* Slight inconsistency in internal service structure

---

## Alternatives Considered

### Pure CQRS for all services

Rejected due to unnecessary complexity for simple services.

### Pure layered architecture

Rejected due to insufficient structure for complex domains.

### Fully free-form architecture per service

Rejected due to lack of governance and inconsistency.

---

## Related ADRs

* ADR-005 Service Communication Strategy
* ADR-007 Platform Library Strategy
* ADR-008 CQRS & Mediator Strategy
