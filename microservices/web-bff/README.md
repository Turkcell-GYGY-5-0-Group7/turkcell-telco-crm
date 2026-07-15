# web-bff

| Field | Value |
| --- | --- |
| Port | 9020 |
| Architecture Mode (ADR-004) | Simple Service Layer |
| Infrastructure Profile (ADR-006) | Stateless (no primary store) |
| Owning sprint | [Sprint 16](../../docs/tasks/sprint-16-web-frontend/README.md) (post-MVP) |
| Status | In progress (16.1.1 gateway client + token relay done) |

Backend-for-Frontend for the SvelteKit web channel (ADR-022). Aggregates and shapes domain service
responses for the frontend. It does NOT perform the Keycloak token exchange: the browser runs the
Authorization Code + PKCE flow directly against Keycloak (ADR-011 Section 5); web-bff only RELAYS the
caller's bearer token onto its outbound calls. It reaches domain services exclusively through the API
gateway (`/api/v1/**`), never a domain-service port directly (ADR-022, ADR-011 Section 2). Contract:
[web-bff](../../docs/api-contracts/web-bff.md).

Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
bootstrap. Reuse platform capabilities first ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl web-bff spring-boot:run
```
