# ADR-010 Service Discovery & Configuration Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

The platform must operate in both:

* Local development environments
* Kubernetes production environments

We require flexible service discovery and configuration mechanisms that support both environments without code changes.

---

## Decision

We adopt a **dual-mode strategy**:

---

# 1. Service Discovery

## Development Mode

* Eureka is used for service discovery

## Production Mode

* Kubernetes native DNS-based service discovery

---

## Rule

Services MUST NOT depend directly on Eureka APIs.

Discovery must be abstracted via Spring Cloud compatibility layer.

---

# 2. Configuration Management

## Development Mode

* Spring Cloud Config Server

## Production Mode

* Kubernetes ConfigMaps and Secrets

---

## Rule

Configuration MUST NOT be hardcoded in services.

All configuration must be externalized.

---

# 3. Environment Switching Strategy

Environment determines implementation:

```text id="k1p9lm"
DEV → Eureka + Config Server
PROD → Kubernetes DNS + ConfigMaps
```

---

# 4. Gateway Integration

* Spring Cloud Gateway is entry point
* Routes resolved dynamically via discovery mechanism

---

# 5. Failover Strategy

* Services must tolerate discovery system failure
* Cached service registry allowed

---

## Consequences

### Positive

* Flexible environment support
* No code changes between dev and prod
* Kubernetes-native production readiness

### Negative

* Dual system complexity
* Additional testing overhead

---

## Alternatives Considered

### Kubernetes-only approach

Rejected due to local development limitations.

### Eureka-only approach

Rejected due to production scalability concerns.

---

## Related ADRs

* ADR-003 Technology Stack
* ADR-005 Service Communication Strategy
