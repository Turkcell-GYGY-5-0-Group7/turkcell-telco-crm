# ADR-020 Platform Maven Architecture

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform consists of:

* multiple microservices
* shared platform capabilities (CQRS, security, observability, outbox, inbox)
* Spring Boot auto-configuration modules
* event-driven infrastructure

We need a **centralized but modular Maven architecture** that ensures:

* strict version control of platform components
* independent evolution of microservices
* safe reuse of shared infrastructure
* avoidance of dependency chaos ("jar hell")
* clean separation between platform and business logic

---

## Decision

We adopt a **multi-layer Maven platform architecture** composed of:

1. Platform BOM (Bill of Materials)
2. Platform Core Modules
3. Platform Starters
4. Internal Utilities (optional shared kernel)

---

# 🧩 1. Top-Level Structure

```text id="m1k9lm"
platform/
├── platform-bom/
├── platform-core/
├── platform-starters/
├── platform-autoconfigure/
├── platform-cqrs/
├── platform-security/
├── platform-outbox/
├── platform-inbox/
├── platform-observability/
├── platform-event-contracts/
└── platform-common/
```

---

# 📌 2. Platform BOM (CRITICAL FOUNDATION)

## Responsibility

The BOM defines:

* dependency versions
* Spring Boot alignment
* Kafka / PostgreSQL / Redis versions
* internal platform module versions

---

## Rule

ALL services MUST import ONLY:

```xml id="b1k9lm"
<dependencyManagement>
    <dependency>
        <groupId>com.telco.platform</groupId>
        <artifactId>platform-bom</artifactId>
        <version>${platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

---

## BOM Controls:

* Spring Boot version
* Spring Cloud version
* Kafka client version
* Avro version
* PostgreSQL driver
* Redis client
* OpenTelemetry libs

---

# 🧠 3. Platform Core Modules

## Responsibility

Core modules define **business-agnostic infrastructure primitives**:

* CQRS engine
* Mediator pipeline
* Outbox engine
* Inbox engine
* security context
* observability context

---

## Rule

Core modules MUST NOT depend on Spring Boot starters directly.

They are framework-agnostic logic.

---

## Example:

```text id="c1k9lm"
platform-cqrs
platform-mediator
platform-outbox
platform-inbox
```

---

# 🚀 4. Platform Starters (SPRING INTEGRATION LAYER)

## Responsibility

Starters wrap core modules into Spring Boot auto-configuration.

---

## Example:

```text id="s1k9lm"
platform-starter-mediator
platform-starter-security
platform-starter-outbox
platform-starter-observability
```

---

## Rule

Starters:

* contain @AutoConfiguration
* register beans
* expose configuration properties
* MUST NOT contain business logic

---

# ⚙️ 5. Autoconfigure Layer

## Responsibility

Bridges Spring Boot lifecycle with platform modules.

```text id="a1k9lm"
platform-autoconfigure
```

Rules:

* conditional beans (@ConditionalOnMissingBean)
* environment-based wiring
* extension points

---

# 📡 6. Platform Event Contracts

```text id="e1k9lm"
platform-event-contracts
```

Contains:

* Avro schemas
* shared event definitions
* versioned events

---

# 🧩 7. Platform Common (OPTIONAL - CAREFUL)

Used for:

* ApiResult
* ApiError
* base exceptions
* utility primitives

⚠️ Rule:

This module MUST stay minimal to avoid coupling.

---

# 🔗 8. Dependency Hierarchy (VERY IMPORTANT)

```text id="d1k9lm"
platform-bom
    ↓
platform-core
    ↓
platform-starters
    ↓
microservices
```

AND:

```text id="d2k9lm"
platform-core (NO Spring dependency)
platform-starters (Spring dependency allowed)
microservices (only starters allowed)
```

---

# 🚫 9. Forbidden Rules

Microservices MUST NOT:

* depend on platform-core directly
* override BOM versions manually
* include Spring Boot versions independently
* bypass starters
* duplicate platform logic

---

# 🧠 10. Versioning Strategy

Platform follows:

```text id="v1k9lm"
MAJOR.MINOR.PATCH
```

Rules:

* Major → breaking changes (CQRS, outbox, etc.)
* Minor → new features (new behaviors, starters)
* Patch → bug fixes only

---

# ⚡ 11. Service Consumption Model

Microservices only do:

```xml id="m2k9lm"
<dependency>
    <groupId>com.telco.platform</groupId>
    <artifactId>platform-starter-security</artifactId>
</dependency>
```

NO direct platform logic usage.

---

## Consequences

### Positive

* Strong platform governance
* No dependency chaos
* Fully reusable infrastructure
* Clean separation of concerns
* Enables AI-driven code generation safely

### Negative

* Requires strict discipline
* More initial setup complexity
* Versioning coordination required

---

## Alternatives Considered

### Single shared library

Rejected due to tight coupling and scalability issues.

### Direct service-level duplication

Rejected due to inconsistency and maintenance overhead.

---

## Related ADRs

* ADR-018 Platform Starter Dependency Model
* ADR-008 CQRS & Mediator Strategy
* ADR-009 Event Driven Architecture
