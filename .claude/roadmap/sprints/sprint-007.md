# Sprint 007 - Hardening and Release

| Field | Value |
| --- | --- |
| Sprint | 007 |
| Epic | EPIC-009 Hardening and Release |
| Phase | P5 |
| Status | TODO |
| Progress | 0/6 |
| Started | - |
| Completed | - |

## Goal

Meet non-functional targets and prepare the MVP release candidate (Phase P5).

## Tasks

| ID | Task | Status | Notes |
| --- | --- | --- | --- |
| T-033 | Performance validation: API p95 < 300 ms; bill-run 100K < 30 min (NFR-01, NFR-02) | TODO | Load tests in CI |
| T-034 | Observability rollout: OpenTelemetry, Prometheus, Grafana, Loki, Tempo (NFR-07..09) | TODO | Includes BL-03 tracer export |
| T-035 | Security hardening: mTLS, PII AES-GCM encryption, audit logging (NFR-05, NFR-06, NFR-12) | TODO | Per ADR-011 |
| T-040 | PII telemetry masking: `@Sensitive` + masking ObjectMapper (Layer A) and Logback regex backstop (Layer B); `telco.platform.logging.masking.*` config | TODO | Per ADR-021; default PARTIAL keep-last-4. Platform-wide, applies to all services |
| T-036 | Kubernetes deployment, HPA, rolling updates and rollback (ADR-014) | TODO | DevOps |
| T-037 | Resilience validation: circuit breaker, retry, bulkhead (NFR-10) | TODO | Resilience4j |

## Definition of Done

- All MVP acceptance criteria pass and NFR targets are demonstrably met.
- Kubernetes deploy with HPA and rollback validated; release candidate signed off.

## Dependencies

- All prior sprints; BL-01 infra and BL-03 tracer export.

## Agent Assignments

- Observability Agent -> tracing, metrics, logging
- Security Agent -> mTLS, encryption, audit
- DevOps Agent -> Kubernetes, HPA, CI/CD
- QA Agent -> performance and resilience tests
- Tech Lead Agent -> release sign-off
