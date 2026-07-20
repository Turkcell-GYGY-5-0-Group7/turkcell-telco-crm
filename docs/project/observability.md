# Observability

Full authority: [ADR-012: Observability Strategy](../adr/ADR-012-observability-strategy.md).

## The stack

| Concern | Technology | Local port |
| --- | --- | --- |
| Distributed tracing | OpenTelemetry Collector -> Tempo | Collector 4317 (gRPC) / 4318 (HTTP), Tempo 3200 |
| Metrics | Prometheus | 9090 |
| Dashboards / alerting | Grafana | 3000 |
| Log aggregation | Loki | 3100 |

Bring it all up locally with `make infra-observability` (adds to whatever core/platform/apps
profiles are already running). Grafana ships with a platform-overview dashboard (HTTP p95 latency
by service, among other panels) and a Kafka/billing-ops dashboard, plus alert rules for downtime,
elevated error rates, Kafka consumer lag, and database connection failures.

## The one non-negotiable rule

**Every request carries a `traceId` and a `correlationId`, end to end**, including across Kafka.
`starter-observability`'s `CorrelationFilter` wires this automatically:

- Every HTTP request gets a traceId/correlationId if it doesn't already carry one from an
  upstream caller.
- Every structured log line includes `timestamp`, `serviceName`, `traceId`, `correlationId`,
  `userId`, and `eventName` via MDC.
- Kafka consumers **continue** the trace context from the producing span - an async hop must
  never break the trace chain. This is the property that makes a saga like new-line onboarding
  (order -> payment -> subscription -> notification) debuggable as one trace instead of four
  unrelated ones.

## Structured logging

Logs are JSON (via `logstash-logback-encoder`), shipped to Loki. PII masking on the log body is
layered: `@Sensitive` + a Jackson masking module handle the structured JSON path (the primary
control); a Logback pattern-converter is a free-text backstop for anything rendered as a plain
message or stack trace. See [Security & Identity](security-and-identity.md#pii-protection) for
the known limitation (the JSON path does not run Logback pattern words, so masking there is
Layer-A-only - never interpolate raw PII into a log message).

## What to check first when something looks wrong

1. **Grafana platform-overview dashboard** - is the service up, and what does its p95 latency and
   error rate look like right now.
2. **Tempo, by traceId** - pull the traceId from a failing request's `ApiResult` error envelope
   (`meta.traceId`) or from a log line, and look at the full span tree across every service and
   Kafka hop it touched.
3. **Loki, by traceId or correlationId** - every log line from every service for that one request,
   correlated, in order.
4. **Prometheus** - Kafka consumer lag and circuit-breaker state (Resilience4j exposes breaker
   state as a metric) if the symptom looks like a downstream dependency problem rather than a bug
   in the service itself.

## Resilience signals

Resilience4j circuit breakers on internal REST calls (for example order-service's calls to
customer-service and product-catalog-service) expose their state as Prometheus metrics and are
visualized on the circuit-breakers Grafana dashboard - open/half-open/closed transitions are a
first signal that a downstream dependency is degraded, before a human ever has to guess.

## Chaos engineering (Sprint 20)

`deploy/chaos/` vendors a Chaos Mesh Helm chart plus three fault-injection experiments
(pod-kill on order-service, latency between order-service and customer-service, a Kafka network
partition against billing-service) and a game-day runbook, built specifically to exercise the
observability stack above under real failure - the point is to prove the dashboards and alerts
actually catch a problem, not just that the dashboards exist. See
`deploy/chaos/GAMEDAY-RUNBOOK.md`.
