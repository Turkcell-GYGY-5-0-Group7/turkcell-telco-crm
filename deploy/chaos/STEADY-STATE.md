# Steady-State Hypotheses (Sprint 20, Feature 20.2)

> Feature: [20.2 Steady-state hypotheses and observability wiring](../../docs/tasks/sprint-20-chaos-engineering/20.2-steady-state-hypotheses-and-observability-wiring.md)
> Subtasks: 20.2.1 (hypothesis mapping table), 20.2.2 (dashboard pre-flight check),
> 20.2.3 (baseline metric snapshot procedure)

This document reuses the three Sprint 13.3 Grafana dashboards and the existing
Prometheus alert rules (`deploy/helm/dependencies/files/observability/alerts.yml`) as the
sole observability surface for the 20.3 chaos experiments. No new dashboard, panel,
metric, or alert is introduced here (README Feature 20.2 constraint, extends ADR-012).

## Corrections to upstream task text (read this before the table)

Two inaccuracies in the sprint documentation are corrected in this file rather than
silently propagated:

1. **No p99 latency panel exists.** The Sprint 20 README's Feature 20.2 notes describe
   `platform-overview` as having "p95/p99 latency, error rate, throughput" panels. Reading
   the actual dashboard JSON
   (`deploy/helm/dependencies/files/observability/grafana/dashboards/platform-overview.json`)
   shows only **"HTTP p95 Latency by Service (s)"** — there is no p99 panel. The mapping
   table below cites p95 only.
2. **The latency-injection pairing in 20.3.2 is factually wrong.** The 20.3 task file
   (`docs/tasks/sprint-20-chaos-engineering/20.3-experiment-library-pod-kill-latency-injection-network-partition.md`)
   and the README's Feature 20.3 notes name `order-service -> payment-service` as the
   Resilience4j-guarded call to inject latency against. Reading
   `microservices/order-service/src/main/java/com/telco/order/config/ResilienceConfig.java`
   shows order-service registers exactly two circuit breakers — `customer-service` and
   `product-catalog-service` — for its synchronous outbound calls.
   `order-service <-> payment-service` is Kafka-only (asynchronous, via the outbox/inbox
   pattern), so there is no synchronous call there for `NetworkChaos` delay injection to
   affect and no circuit breaker to observe tripping. The corrected pairing used below is
   **`order-service -> customer-service`** (breaker name `customer-service`).
   Additionally, `ResilienceConfig.java` configures only
   `failureRateThreshold(50)`, `slidingWindowSize(10)`, `waitDurationInOpenState(30s)`,
   and `permittedNumberOfCallsInHalfOpenState(3)` — there is no
   `slowCallDurationThreshold` configured. Injected latency therefore does not trip the
   breaker via a slow-call path; it trips it via the **failure-rate path**: the injected
   delay must be large enough that calls time out (counted as failures) so that
   `>= 50%` of the last 10 calls in the sliding window fail, which then opens the breaker.
   The hypothesis and abort threshold below are worded accordingly.

Similarly, `deploy/RUNBOOK.md`'s Observability content is in **Section 10**, not
Section 9 as the 20.2/20.2.2/20.2.3 task files assume — cited correctly throughout this
document.

## 20.2.1 - Steady-state hypothesis mapping table

| Experiment | Steady-State Hypothesis (plain language) | Dashboard | Panel | Alert Rule | Abort Threshold |
| --- | --- | --- | --- | --- | --- |
| Pod-kill (`order-service`) | Killing one `order-service` pod does not reduce served traffic or raise p95 latency past its NFR-07 budget — the PDB (`minAvailable: 1`) keeps at least one pod serving and the HPA/Deployment reschedules the killed pod without a user-visible gap. | `platform-overview` | "HTTP p95 Latency by Service (s)" | `ApiHighP95Latency` (`histogram_quantile(0.95, sum by(le, job) (rate(http_server_requests_seconds_bucket[5m]))) > 0.3`, for 2m) | Abort immediately if `ApiHighP95Latency` fires for `order-service`, or if the panel shows p95 > 0.3s sustained past 2 minutes, or if `order-service` request throughput drops to zero (no pod serving). |
| Latency injection (`order-service -> customer-service`) | Injecting network delay on the `order-service -> customer-service` call does not cascade into upstream failures — once injected latency causes enough timeouts to push the failure rate over 50% across the 10-call sliding window, the `customer-service`-named circuit breaker on `order-service` opens (per the failure-rate path, since no `slowCallDurationThreshold` is configured) and calls fail fast instead of piling up. | `circuit-breakers` | "Circuit Breaker State per Instance" (state transition to `open`), corroborated by "Circuit Breaker Calls (success / failed / not-permitted)" | `CircuitBreakerOpen` (`resilience4j_circuitbreaker_state{state="open"} == 1`, for 30s) | Abort if the breaker does NOT open within the expected sliding window (10 calls) after latency injection starts (fault is not being absorbed as designed), or if `order-service`'s own p95 latency (`platform-overview`, "HTTP p95 Latency by Service (s)") breaches 0.3s for longer than 2 minutes before the breaker opens. |
| Network partition (`billing-service` <-> Kafka) | Isolating `billing-service` from Kafka does not lose messages — consumer lag rises while the partition is in effect but drains back down once the partition heals (no `KafkaConsumerLagHigh` sustained past recovery), consistent with the outbox/inbox pattern's at-least-once, idempotent recovery (ADR-009/019). | `kafka-billing-ops` | "Kafka Consumer Lag by Group" (primary), "Bill-Run Duration" (secondary — watch for stalled bill-runs during the partition) | `KafkaConsumerLagHigh` (`kafka_consumer_fetch_manager_records_lag > 1000`, for 5m) | Abort if lag has not started draining within 5 minutes of the partition healing, or if `KafkaConsumerLagHigh` is still firing 5 minutes after the `NetworkChaos` partition resource is deleted. |

No row above proposes a new panel, alert, or metric; every dashboard/panel/alert cited
is verified against the real dashboard JSON and `alerts.yml` referenced in this repo.

## 20.2.2 - Pre-flight check

Before running any 20.3 experiment, an operator confirms all three dashboards are
reachable and rendering live data. Port-forward commands are taken verbatim from
`deploy/RUNBOOK.md` Section 10 ("Observability"):

```sh
kubectl -n telco port-forward svc/grafana    3000:3000   # dashboards (platform-overview, kafka-billing, circuit-breakers)
kubectl -n telco port-forward svc/prometheus 9090:9090   # metrics + alerts
```

Grafana is then reachable at `http://localhost:3000` and Prometheus at
`http://localhost:9090`. What "populated" looks like, per dashboard:

- **`platform-overview`**: "HTTP p95 Latency by Service (s)" and "HTTP Request Rate by
  Service (req/s)" show a non-flat, moving line reflecting real traffic against
  `order-service`/`customer-service`/etc. — not a flat zero line or a "No data"
  placeholder. "JVM Heap Used per Service (bytes)" shows a nonzero value per running pod.
- **`kafka-billing-ops`**: "Kafka Consumer Lag by Group" shows series for the active
  consumer groups (even if lag is near zero at steady state) rather than an empty graph;
  "Bill-Run Duration" and "Invoice Generation Count (total)" show historical data points
  if any bill-run has executed, or are legitimately empty if none has run yet in this
  environment (documented as expected, not a failure, if no billing job has fired).
- **`circuit-breakers`**: "Circuit Breaker State per Instance" shows all registered
  breakers (`customer-service`, `product-catalog-service`, and any others) in the
  `closed` state (value 1 on the `closed` series) before any fault is injected; "Circuit
  Breaker Calls (success / failed / not-permitted)" shows a nonzero `success` series and
  a near-zero `failed`/`not_permitted` series at steady state.

If any panel shows "No data" where traffic is expected, do not proceed with the
corresponding 20.3 experiment — treat it as a blocked pre-flight check per 20.2.2's
acceptance criteria (dashboards must be reachable and populated before chaos is run).

No dashboard JSON, panel, or Grafana provisioning file under `deploy/helm/dependencies`
is modified by this document or by this task.

## 20.2.3 - Baseline capture

Immediately before running a 20.3 experiment, capture a "before" snapshot of each mapped
panel's underlying metric via a Prometheus port-forward
(`kubectl -n telco port-forward svc/prometheus 9090:9090`, from `deploy/RUNBOOK.md`
Section 10), querying `http://localhost:9090/graph` or the HTTP API
(`/api/v1/query`). Repeat the same query immediately after the experiment (and again
once the system has recovered) to get a before/during/after comparison for the 20.4
post-game-day template. One query per mapped panel, extracted verbatim from
`deploy/helm/dependencies/files/observability/alerts.yml` (not invented):

- **p95 latency** (backs `platform-overview` / "HTTP p95 Latency by Service (s)",
  used by the pod-kill and latency-injection experiments — scope to the relevant `job`
  label, e.g. `order-service`, when comparing before/after):
  ```promql
  histogram_quantile(
    0.95,
    sum by(le, job) (
      rate(http_server_requests_seconds_bucket[5m])
    )
  )
  ```

- **Circuit breaker state** (backs `circuit-breakers` / "Circuit Breaker State per
  Instance", used by the latency-injection experiment — filter to
  `name="customer-service"` for the corrected pairing):
  ```promql
  resilience4j_circuitbreaker_state{state="open"}
  ```

- **Kafka consumer lag** (backs `kafka-billing-ops` / "Kafka Consumer Lag by Group",
  used by the network-partition experiment):
  ```promql
  kafka_consumer_fetch_manager_records_lag
  ```

Each query can be run before and after an experiment via
`kubectl -n telco port-forward svc/prometheus 9090:9090` followed by either the
Prometheus UI (`http://localhost:9090/graph`) or a direct HTTP call, e.g.:

```sh
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=resilience4j_circuitbreaker_state{state="open"}' | jq .
```

## Verification status (this session)

Docker Desktop was confirmed down for this authoring session; no cluster was available.
The following acceptance criteria are documentation-shape only and are satisfied by this
file:

- 20.2.1: one row per 20.3 experiment, each citing a real dashboard/panel/alert, no new
  panel/alert/metric proposed. **Satisfied.**
- 20.2.2: a concrete, runnable port-forward command sequence documented; no dashboard
  JSON/provisioning file modified. **Satisfied.**
- 20.2.3: one PromQL query per mapped panel, extracted from the real `alerts.yml`,
  documented as runnable via the Prometheus port-forward. **Satisfied.**

The following acceptance criteria require a live cluster and are **NOT verified this
session** (Docker Desktop down; follow-up pass required once Docker recovers):

- 20.2.2: that Grafana is actually reachable via the documented port-forward and that
  `platform-overview`, `kafka-billing-ops`, and `circuit-breakers` actually render live,
  non-empty data on the target Kind cluster.
- 20.2.3: that an operator can actually run the three PromQL queries above against a live
  Prometheus and get non-error responses/values.
