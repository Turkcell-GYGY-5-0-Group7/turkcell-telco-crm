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

---

## Amendment — 2026-06-23

### Config Server backend and file layout

The Spring Cloud Config Server uses the **native filesystem backend** with config files
located at:

```
microservices/configs/
```

This directory sits at the `microservices/` root for direct IDE and editor access during
development. It is NOT packaged inside the config-server jar; the server reads it via
`file:` search-locations relative to its working directory.

Directory layout:

```
microservices/configs/
  application.yml              # shared defaults for all services
  application-{env}.yml        # shared per-env overrides (dev/test/staging/prod)
  <service-name>/
    application.yml            # service-specific base config
    application-{env}.yml      # service-specific per-env overrides
```

The `{application}` placeholder in `search-locations` is resolved by Spring to the
client's `spring.application.name`, routing each service to its own subdirectory.

**Running locally**: `mvn -pl config-server spring-boot:run` from `microservices/` sets
the working directory to `microservices/config-server/`, so `../configs` resolves
correctly by default.

**Docker**: Mount `microservices/configs/` into the container and set
`CONFIG_DIR=/path/to/configs` to override the default relative path.

### Bootstrap pattern for all domain services

Every domain service application.yml MUST contain only:

```yaml
spring:
  application:
    name: <service-name>
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import: optional:configserver:${CONFIG_SERVER_URI:http://localhost:8888}
```

The `optional:` prefix ensures the service starts without config-server in test contexts
where Testcontainers or local overrides apply directly.

### Infrastructure server exemption

config-server, discovery-server, and api-gateway bootstrap themselves and are exempt from
the `spring.config.import` requirement. They carry their own self-contained application.yml.

### Environment switching

Set `SPRING_PROFILES_ACTIVE` to one of: `dev`, `test`, `staging`, `prod`.
Services MUST NOT hard-code environment-specific values; use `${ENV_VAR:default}` placeholders.
