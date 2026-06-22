# ADR-006 Database Strategy

Status: Accepted
Date: 2026-06-19

## Context

The Telco CRM platform requires persistent storage for multiple bounded contexts such as customers, orders, billing, usage, and notifications.

We need a strategy that:

* Ensures service autonomy
* Prevents cross-service data coupling
* Supports scalability
* Allows polyglot persistence when necessary
* Maintains operational simplicity

## Decision

### Primary Database Model

Each microservice MUST use:

> Database-per-Service model

### Naming Convention

```text id="p4k8w1"
<service-name>-db
```

Example:

* customer-db
* order-db
* billing-db

### Primary Database Technology

* PostgreSQL 17 is the default database

### Secondary Databases

* MongoDB 8.x (exception only)
* Redis (cache, not source of truth)

## MongoDB Usage Rule

MongoDB is allowed ONLY for:

* Document-heavy models
* Flexible schema projections
* Read-optimized views
* Non-transactional data

It MUST NOT be used for:

* Core transactional data
* Financial records
* Orders or billing logic

## Cross-Service Data Rule

No service may directly access another service’s database.

Data sharing MUST occur via:

* Kafka events
* gRPC APIs
* REST APIs (external only)

## Migration Strategy

* Flyway is mandatory for all PostgreSQL schemas
* Each service owns its migrations independently

## Consequences

### Positive

* Strong service isolation
* Independent scaling
* Reduced coupling
* Easier evolution of services

### Negative

* Data duplication via events
* More operational overhead

## Alternatives Considered

### Shared Database

Rejected due to tight coupling.

### Polyglot-first approach

Rejected due to operational complexity.

## Related ADRs

* ADR-005 Service Communication Strategy
* ADR-009 Event Driven Architecture
