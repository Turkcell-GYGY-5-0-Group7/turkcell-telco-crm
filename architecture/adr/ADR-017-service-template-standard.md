# ADR-017 Service Template Standard

Status: Accepted
Date: 2026-06-19

---

## Context

The platform will contain dozens of microservices.

We need a standardized service blueprint to ensure:

* Consistent architecture mode selection
* Fast service creation
* AI-assisted generation support
* Uniform platform integration
* Reduced onboarding time

---

## Decision

We define a **standard microservice template** for all services.

---

# 1. Base Structure

```text id="s1k9lm"
<service-name>/

├── src/main/java/com/telco/<service>/
├── src/test/java/
├── Dockerfile
├── pom.xml
├── README.md
├── CLAUDE.md
└── src/main/resources/
```

---

# 2. Mandatory Components

Every service MUST include:

* Architecture Mode declaration (ADR-004)
* API versioning (/api/v1)
* ApiResult usage
* Observability instrumentation
* Flyway migrations
* Platform starter dependencies

---

# 3. Architecture Mode Declaration

Each service MUST declare:

```text id="a1k9lm"
Architecture Mode: CQRS + MEDIATOR
```

or:

```text id="a2k9lm"
Architecture Mode: SIMPLE SERVICE LAYER
```

---

# 4. Platform Integration Rule

Services MUST ONLY depend on:

* platform starters
* NOT internal platform modules

---

# 5. Default Dependencies

All services include:

* starter-api
* starter-security
* starter-observability

Optional:

* starter-outbox
* starter-inbox
* starter-mediator

---

## Consequences

### Positive

* Rapid service generation
* Consistency across all services
* Strong AI compatibility
* Reduced architectural drift

### Negative

* Less flexibility for experimental services
* Requires strict governance

---

## Related ADRs

* ADR-007 Platform Library Strategy
* ADR-004 Architecture Style
