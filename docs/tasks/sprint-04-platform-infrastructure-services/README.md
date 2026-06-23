# Sprint 04 - Platform Infrastructure Services

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/3 | 2026-06-22 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Stand up the three edge/platform services from the analysis document: config-server (8888),
discovery-server (8761), and api-gateway (8080). These provide centralized configuration, service
registry, and the single secured entry point (routing, JWT validation hook, header propagation,
rate limiting, correlationId injection). They are configuration-only services with no domain logic.

## Included Epics

- Epic 4: Platform Infrastructure Services

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 4.1 | Config Server | TODO | [4.1-config-server.md](4.1-config-server.md) |
| 4.2 | Discovery Server | TODO | [4.2-discovery-server.md](4.2-discovery-server.md) |
| 4.3 | API Gateway | TODO | [4.3-api-gateway.md](4.3-api-gateway.md) |

## Sprint Deliverables

- config-server (8888) with encrypted secrets, discovery-server (8761), and api-gateway (8080) with
  routing, JWT validation, identity header propagation, correlationId injection, Redis rate limiting,
  and OpenAPI aggregation.
- Service template registers with discovery and pulls config from config-server.

## Exit Criteria

- All three infrastructure services start via `make infra-up` plus their own run, and a routed
  request flows: gateway -> JWT check -> header propagation -> discovery-resolved service.
- Unauthenticated access to protected routes returns 401; rate limit returns 429 past 100 req/min;
  every request carries a correlationId.
- FR-IAM-02 and FR-IAM-03 are satisfied at the gateway (identity issuance itself lands in Sprint 05).
</content>
