# config-server

| Field | Value |
| --- | --- |
| Port | 8888 |
| Architecture Mode (ADR-004) | N/A (infrastructure) |
| Infrastructure Profile (ADR-006) | none |
| Status | Skeleton (no business code) |

Spring Cloud Config Server. Serves centralized configuration to every other service (ADR-010).

## Central config

All service configuration lives here, under [`src/main/resources/config/`](src/main/resources/config/),
served via the native (filesystem) backend. This is the single place to manage configuration; services
do not carry their own runtime config (only a minimal `spring.config.import` bootstrap).

Layout (Spring Cloud Config resolution, later overrides earlier):

1. `application.yml` - shared defaults (all services, all environments)
2. `application-{dev|test|staging|prod}.yml` - shared per-environment overrides
3. `<service>.yml` - per-service defaults
4. `<service>-{dev|test|staging|prod}.yml` - per-service per-environment overrides (highest precedence)

Secrets are stored as `{cipher}...` encrypted values, never plaintext (ADR-011).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=native mvn -pl config-server spring-boot:run
```

This server is self-contained: it does not import config from itself. In production, configuration is
provided by Kubernetes ConfigMaps/Secrets instead of this server (ADR-010).
