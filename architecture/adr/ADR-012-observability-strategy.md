# ADR-012 Observability Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform is a distributed microservices system with:

* Synchronous communication (REST, gRPC)
* Asynchronous communication (Kafka events)
* Multi-layer architecture (BFF, Gateway, Services)
* Complex business workflows (billing, orders, payments)

We require full observability across:

* Logs
* Metrics
* Traces
* Event flows

To ensure:

* Fast debugging in distributed systems
* End-to-end request tracking
* Performance monitoring
* Failure root-cause analysis

---

## Decision

We will adopt a **full OpenTelemetry-based observability stack**.

---

# 1. Core Observability Stack

* OpenTelemetry (standard instrumentation)
* Prometheus (metrics)
* Grafana (visualization)
* Loki (log aggregation)
* Tempo (distributed tracing)

---

# 2. Correlation Model (Critical Design Rule)

Every request MUST have:

* `correlationId`
* `traceId`
* `spanId`

### Rule:

All logs, events, and requests MUST propagate correlation context.

---

# 3. Request Flow Tracking

```text id="o1k9lm"
Client Request
 → API Gateway
 → BFF
 → Microservice
 → Database / Kafka
 → Other Services
```

Each step MUST preserve:

* traceId
* correlationId

---

# 4. Logging Standard

All services MUST use structured logging:

### Required fields:

* timestamp
* serviceName
* traceId
* correlationId
* userId (if available)
* eventName (if applicable)

---

# 5. Metrics Standard

Each service MUST expose:

* request latency
* error rate
* throughput
* JVM metrics
* database metrics
* Kafka consumer lag

---

# 6. Distributed Tracing

* Every HTTP/gRPC request MUST create a trace span
* Kafka consumers MUST continue trace context
* Async boundaries MUST NOT break trace chains

---

# 7. Event Observability

Kafka events MUST include:

* eventId
* eventType
* traceId
* correlationId
* timestamp

---

# 8. Alerting Strategy

Alerts MUST be configured for:

* service downtime
* high error rates
* Kafka lag spikes
* database connection failures

---

## Consequences

### Positive

* Full system visibility
* Fast debugging in distributed systems
* Strong production monitoring
* End-to-end traceability

### Negative

* Increased infrastructure cost
* Requires strict instrumentation discipline
* Slight runtime overhead

---

## Alternatives Considered

### Logging-only approach

Rejected due to lack of traceability.

### Vendor-specific observability (Datadog, New Relic)

Rejected due to vendor lock-in.

---

## Related ADRs

* ADR-011 Security Foundation
* ADR-009 Event Driven Architecture
* ADR-005 Service Communication Strategy
