# ADR-014 CI/CD Strategy

Status: Accepted
Date: 2026-06-19

---

## Context

The platform consists of multiple microservices and shared platform modules.

We require a CI/CD strategy that supports:

* Fast feedback loops for developers
* Safe deployments to Kubernetes
* Versioned releases of services and platform libraries
* Automated testing and validation
* Infrastructure consistency

---

## Decision

We adopt a **GitHub Actions-based CI/CD pipeline with environment-based deployment strategy**.

---

# 1. CI Pipeline (Per Service)

Every microservice MUST run:

```text id="c1k9lm"
Build → Test → Package → Static Analysis → Security Scan
```

---

# 2. Build Rules

* Maven is the single build tool
* Java 21 is enforced
* Platform BOM is used for dependency consistency

---

# 3. Testing Stage

CI MUST run:

* Unit tests (mandatory)
* Integration tests (mandatory for merge)
* Contract tests (if applicable)

---

# 4. Static Analysis

Mandatory tools:

* Checkstyle
* SpotBugs
* Optional: SonarQube

---

# 5. Artifact Strategy

Each service produces:

* Docker image
* Versioned JAR
* Metadata (commit SHA, version, build info)

---

# 6. Deployment Strategy

We use **Kubernetes-based deployment**:

### Environments:

* Development
* Test
* Staging
* Production

---

# 7. Deployment Model

### Preferred:

* Rolling updates in Kubernetes

### Optional (future):

* Blue/Green deployments
* Canary deployments for critical services

---

# 8. Platform Libraries Deployment

Platform modules:

* Are versioned together
* Are published internally (Maven repo)
* Are consumed via BOM

---

# 9. Git Strategy

* Main branch = production-ready
* Feature branches = isolated development
* PR required for all merges

---

# 10. Rollback Strategy

* Kubernetes rollback via previous deployment revision
* Stateless services allow instant rollback

---

## Consequences

### Positive

* Predictable deployments
* Strong validation pipeline
* Safe production releases
* Scalable CI system

### Negative

* Pipeline complexity increases
* Longer build times as system grows

---

## Alternatives Considered

### Manual deployment

Rejected due to risk and inconsistency.

### GitOps-only (ArgoCD only)

Deferred to future ADR due to complexity.

---

## Related ADRs

* ADR-013 Testing Strategy
* ADR-010 Service Discovery & Configuration Strategy
* ADR-012 Observability Strategy
