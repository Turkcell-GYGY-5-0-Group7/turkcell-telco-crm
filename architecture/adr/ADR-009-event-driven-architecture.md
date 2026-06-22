# ADR-009 Event Driven Architecture

Status: Accepted
Date: 2026-06-19

---

## Context

The platform is heavily event-driven and must support:

* Reliable event publishing
* Cross-service communication
* Event replayability
* Idempotent consumers
* Decoupled microservices

We must ensure:

* No event loss
* No duplicate side effects
* Strong consistency where required (via outbox)
* Event versioning and schema evolution

---

## Decision

We will use a **Kafka-based event-driven architecture** with:

* Transactional Outbox Pattern
* Inbox Pattern (Idempotent Consumers)
* Avro Schema Registry for contracts

---

# 1. Event Flow Architecture

```text id="e1k9qp"
Domain Event
 → Outbox Table
 → Debezium CDC
 → Kafka Topic
 → Consumer Service
 → Inbox Deduplication
 → Handler Execution
```

---

# 2. Outbox Pattern (Producer Side)

### Rules:

* Events MUST be stored in DB first
* Outbox table is part of same transaction as business data
* Debezium is responsible for publishing to Kafka
* No direct Kafka publishing from business logic

---

# 3. Inbox Pattern (Consumer Side)

### Rules:

* Every event MUST have unique eventId
* Consumers MUST store processed event IDs
* Duplicate events MUST be ignored safely
* Processing MUST be idempotent

---

# 4. Event Schema Standard

All events MUST:

* Use Avro schema
* Be versioned (`event.v1`)
* Be backward compatible

Example:

```text id="v2k8lm"
customer.created.v1
customer.updated.v1
```

---

# 5. Kafka Usage Rules

* Kafka is the default event backbone
* Topics are domain-driven
* Partitioning must follow business keys (e.g., customerId)

---

# 6. Event Contract Ownership

* Each service owns its event schemas
* Shared event contracts exist in `platform/event-contracts`
* No shared mutable DTOs between services

---

# 7. Consistency Model

* Strong consistency inside service (DB transaction)
* Eventual consistency across services

---

# 8. Retry & Failure Handling

* Consumers MUST implement retry strategy
* Poison messages go to Dead Letter Queue (DLQ)
* DLQ must be monitored

---

## Consequences

### Positive

* High reliability
* Loose coupling between services
* Scalable architecture
* Replayable event history
* Strong auditability

### Negative

* Increased system complexity
* Requires strict schema governance
* Debugging distributed flows is harder

---

## Alternatives Considered

### Direct service-to-service calls

Rejected due to tight coupling and scaling issues.

### Synchronous-only architecture

Rejected due to poor scalability and resilience.

---

## Related ADRs

* ADR-008 CQRS & Mediator Strategy
* ADR-005 Service Communication Strategy
* ADR-012 Observability Strategy
