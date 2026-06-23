# ADR-005 Service Communication Strategy

Status: Accepted
Date: 2026-06-19
Amended: 2026-06-23 (internal synchronous calls use REST/OpenFeign for the MVP; gRPC deferred to post-MVP - Tech Lead ruling)

## Context

The platform requires both synchronous and asynchronous communication between microservices.

We need:

* Low-latency synchronous calls
* Reliable asynchronous event propagation
* Strong contract enforcement
* Future flexibility for broker replacement
* Support for distributed systems evolution

## Decision

The platform will use a hybrid communication model:

### 1. External Communication (Client → System)

* REST over HTTP
* Versioned APIs (`/api/v1/*`)
* Spring Cloud Gateway as entry point

### 2. Internal Synchronous Communication (Service → Service)

* REST over HTTP (OpenFeign) for internal service-to-service calls in the MVP, wrapped in Resilience4j
  circuit breakers
* gRPC is deferred to post-MVP and will be reconsidered when latency-sensitive, high-throughput
  internal paths emerge
* Synchronous internal calls are the minority; Kafka async (Section 3) is the primary inter-service
  channel

### 3. Internal Asynchronous Communication

* Apache Kafka as primary event bus
* Debezium-based Transactional Outbox pattern
* Inbox pattern for idempotency

## Event Flow Standard

```text id="k8m9qp"
Domain Event → Outbox Table → Debezium → Kafka → Consumers → Inbox Deduplication
```

## Broker Abstraction Rule

Services MUST NOT depend directly on Kafka APIs.

Instead:

* Use platform event-bus abstraction
* Use Spring Cloud Stream where applicable
* Encapsulate broker-specific logic in infrastructure layer

## Contract Rule

All events MUST be:

* Versioned (`*.v1`)
* Schema-driven (Avro + Schema Registry)
* Backward compatible

## Consequences

### Positive

* Clear separation of sync vs async communication
* High scalability
* Broker-agnostic design
* Strong event consistency model

### Negative

* Increased infrastructure complexity
* Requires strict schema governance

## Alternatives Considered

### REST-only communication

Rejected due to scalability limitations.

### Kafka-only communication

Rejected due to latency-sensitive use cases.

### gRPC-only communication

Rejected due to external integration needs.

## Related ADRs

* ADR-003 Technology Stack
* ADR-009 Event Driven Architecture
* ADR-012 Event Contract Standard
