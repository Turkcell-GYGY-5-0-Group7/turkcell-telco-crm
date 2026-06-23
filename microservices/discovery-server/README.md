# discovery-server

| Field | Value |
| --- | --- |
| Port | 8761 |
| Architecture Mode (ADR-004) | N/A (infrastructure) |
| Infrastructure Profile (ADR-006) | none |
| Status | Skeleton (no business code) |

Eureka service registry for development (ADR-010). Services register here in dev; production uses
Kubernetes-native DNS discovery instead (no code change - ADR-010).

Self-bootstrapping: it keeps full local config and does not depend on config-server, so it can start
first and accept registrations even if config-server is briefly unavailable.

## Run (dev)

```bash
mvn -pl discovery-server spring-boot:run
```

Dashboard: http://localhost:8761
