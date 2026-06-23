# Sprint 13 - Observability and Resilience

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/4 | 2026-06-22 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Roll out end-to-end observability across all services (distributed tracing, structured logging,
metrics, dashboards, alerts) and complete the resilience posture (Resilience4j circuit breaker,
retry, bulkhead on every outbound call). The platform primitives exist from Sprint 03; this sprint
verifies they are wired in every service and meet the NFR targets.

Covers NFR-07, NFR-08, NFR-09, NFR-10, NFR-13, and supports NFR-01/NFR-04.

## Included Epics

- Epic 13: Observability and Resilience Rollout

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 13.1 | Distributed Tracing | TODO | [13.1-distributed-tracing.md](13.1-distributed-tracing.md) |
| 13.2 | Logging | TODO | [13.2-logging.md](13.2-logging.md) |
| 13.3 | Metrics, Dashboards, Alerts | TODO | [13.3-metrics-dashboards-alerts.md](13.3-metrics-dashboards-alerts.md) |
| 13.4 | Resilience | TODO | [13.4-resilience.md](13.4-resilience.md) |

## Sprint Deliverables

- End-to-end distributed tracing (including Kafka spans) into Tempo, structured PII-masked JSON logs
  into Loki, Prometheus metrics with Grafana dashboards and alerts, all verified across every service.
- Resilience4j circuit breaker/retry/bulkhead on all outbound calls with behavior tests.

## Exit Criteria

- A single onboarding trace spans all involved services; every request log carries traceId/correlationId.
- Dashboards show p95 latency, bill-run duration, consumer lag, and breaker state with live data;
  SLO-breach alerts fire on simulated breaches.
- All outbound calls are resilience-guarded and verified.
- NFR-07, NFR-08, NFR-09, NFR-10, NFR-13 satisfied.
</content>
