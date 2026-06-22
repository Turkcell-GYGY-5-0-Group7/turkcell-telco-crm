# ADR-011 Security Foundation

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform operates in a distributed microservice environment with:

* External clients (web, mobile, partners)
* Internal services (microservices communication)
* Multiple environments (local, test, production)
* Event-driven asynchronous flows (Kafka)

We require a unified security model that ensures:

* Strong authentication for users and services
* Secure service-to-service communication
* Stateless authentication for scalability
* Centralized identity management
* Support for BFF (Backend-for-Frontend) architecture

---

## Decision

We will implement a **hybrid security architecture** combining:

* Keycloak (Identity Provider)
* JWT-based authentication (user context)
* mTLS (service-to-service trust)
* BFF layer (frontend abstraction)

---

# 1. Identity Provider

Keycloak is the central identity provider for:

* User authentication
* OAuth2 / OpenID Connect
* Role and permission management
* Token issuance

---

# 2. User Authentication Flow

```text id="s1k9lm"
Client → BFF → Keycloak → JWT Token → API Gateway → Microservices
```

### Rules:

* Clients NEVER directly call microservices
* All external traffic MUST go through BFF or API Gateway
* JWT tokens are stateless and self-contained

---

# 3. Service-to-Service Security

All internal communication MUST use:

* Mutual TLS (mTLS)

### Rules:

* Every service has its own identity
* Certificates are issued via Kubernetes or internal PKI
* Services reject non-mTLS traffic in production

---

# 4. Authorization Model

Authorization is handled via:

* JWT claims (user-level permissions)
* Role-Based Access Control (RBAC)
* Optional Attribute-Based Access Control (ABAC) for complex domains

---

# 5. BFF Layer

Each frontend domain MAY have its own BFF:

* Web BFF
* Mobile BFF
* Partner BFF

Responsibilities:

* Token relay
* Aggregation of multiple service calls
* UI-specific transformation
* Security boundary enforcement

---

# 6. Internal Service Rules

* Services MUST validate JWT if request originates externally
* Services MUST trust mTLS identity internally
* No service may bypass authentication

---

# 7. Token Propagation

JWT tokens MUST be propagated across:

* REST calls
* gRPC calls
* Kafka event metadata (if user context is required)

---

# 8. Security Boundaries

| Layer             | Security Mechanism         |
| ----------------- | -------------------------- |
| External clients  | JWT (Keycloak)             |
| API Gateway       | JWT validation             |
| Internal services | mTLS                       |
| Async events      | signed metadata (optional) |

---

## Consequences

### Positive

* Strong end-to-end security model
* Stateless authentication (scalable)
* Clear separation of user vs service identity
* Industry-standard identity management

### Negative

* Operational complexity (Keycloak + PKI)
* Certificate lifecycle management required
* Slight latency overhead due to security layers

---

## Alternatives Considered

### JWT-only (no mTLS)

Rejected due to weak service-to-service trust.

### mTLS-only (no identity provider)

Rejected due to lack of user-level identity management.

### Custom authentication system

Rejected due to security risk and maintenance overhead.

---

## Related ADRs

* ADR-010 Service Discovery & Configuration Strategy
* ADR-012 Observability Strategy
* ADR-005 Service Communication Strategy
