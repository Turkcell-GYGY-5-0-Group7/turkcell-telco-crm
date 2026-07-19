# Chaos Game-Day Runbook (Sprint 20, Feature 20.4)

> Feature: [20.4 Game-Day Runbook](../../docs/tasks/sprint-20-chaos-engineering/20.4-game-day-runbook.md)
> Subtasks: 20.4.1 (prerequisites + execution), 20.4.2 (findings template), 20.4.3 (cross-link)

This is the single operational entry point for running a Sprint 20 chaos game day. A single
operator should be able to bring up the prerequisites, run any of the three 20.3 experiments, watch
the right dashboard, decide whether to abort, and record the outcome by following only this
document - mirroring the structure and tone of [`deploy/RUNBOOK.md`](../RUNBOOK.md) (Sprint 15.5).

This document does not re-derive the install steps, the hypothesis/dashboard/panel/alert mapping,
or the experiment manifests themselves - it cites them:

- [`deploy/chaos/README.md`](README.md) - Chaos Mesh install, CRD verification, dashboard-access
  decision (20.1).
- [`deploy/chaos/STEADY-STATE.md`](STEADY-STATE.md) - the steady-state hypothesis mapping table,
  pre-flight dashboard check, and baseline PromQL queries (20.2), including the two corrections to
  the upstream task text (no p99 panel; corrected `order-service -> customer-service` pairing).
- [`deploy/chaos/experiments/`](experiments) - the three experiment manifests (20.3): exact
  filenames, apply/abort commands, and hypothesis headers repeated in each file's own comments.

---

## 1. Prerequisites

No new CLI tool is introduced by this runbook. An operator needs exactly the base tool set from
[`deploy/RUNBOOK.md` Section 1](../RUNBOOK.md#1-prerequisites):

| Tool | Version verified | Purpose |
| --- | --- | --- |
| Docker | Desktop / Engine (running) | container runtime; Kind runs the node as a container |
| kind | v0.33 | local Kubernetes cluster |
| helm | v4.2 | chart install / upgrade / rollback |
| kubectl | v1.34 | cluster access |
| jq, curl | any recent | smoke test / PromQL queries (20.2.3) |

Beyond that base set, the only additional state required is:

1. **The cluster and platform are already deployed** per `deploy/RUNBOOK.md` Sections 2-4 (Kind
   cluster up, Vault initialized/unsealed, config and secrets in place). This runbook does not
   repeat those steps.
2. **Chaos Mesh is already installed** into the `telco` namespace per
   [`deploy/chaos/README.md`](README.md) ("Install" section) and its CRDs are verified registered
   (`kubectl get crds | grep chaos-mesh.org`, `kubectl explain podchaos.spec`,
   `kubectl explain networkchaos.spec` all resolve to a field list). Do not proceed to Section 2
   below until this is confirmed.
3. **The 20.2.2 pre-flight dashboard check passes.** Before running any experiment, follow
   [`deploy/chaos/STEADY-STATE.md` Section 20.2.2](STEADY-STATE.md#2022---pre-flight-check): port-forward
   Grafana and Prometheus per `deploy/RUNBOOK.md` Section 10, and confirm `platform-overview`,
   `kafka-billing-ops`, and `circuit-breakers` are reachable and rendering live (non-empty) data. If
   any expected panel shows "No data", stop - do not run an experiment against an unobserved system.

No Chaos Mesh dashboard/web UI is used (20.1.3 decision: `dashboard.create: false`). Experiment
status is inspected the same way as any other custom resource:

```sh
kubectl -n telco get podchaos
kubectl -n telco get networkchaos
kubectl -n telco describe podchaos/<name>
kubectl -n telco describe networkchaos/<name>
```

---

## 2. Run an experiment

Common setup for every experiment below:

```sh
# Dashboards (deploy/RUNBOOK.md Section 10)
kubectl -n telco port-forward svc/grafana    3000:3000
kubectl -n telco port-forward svc/prometheus 9090:9090
```

Grafana is then reachable at `http://localhost:3000`, Prometheus at `http://localhost:9090`.

### 2.1 Pod-kill (`order-service`)

- **Manifest**: [`deploy/chaos/experiments/pod-kill-order-service.yaml`](experiments/pod-kill-order-service.yaml)
- **Apply**:
  ```sh
  kubectl apply -f deploy/chaos/experiments/pod-kill-order-service.yaml
  ```
- **Watch**: Grafana dashboard `platform-overview`, panel "HTTP p95 Latency by Service (s)"
  (scope to `job="order-service"`), at `http://localhost:3000` via the port-forward above.
- **Steady-state hypothesis** (summarized from `STEADY-STATE.md` 20.2.1): killing one
  `order-service` pod does not reduce served traffic or push p95 latency past its NFR-07 budget -
  the PDB (`minAvailable: 1`) keeps at least one pod serving and the Deployment/HPA reschedules the
  killed pod without a user-visible gap.
- **Abort criteria**: abort immediately if the `ApiHighP95Latency` alert fires for `order-service`,
  or the panel shows p95 > 0.3s sustained past 2 minutes, or `order-service` request throughput
  drops to zero (no pod serving).
- **Abort command**:
  ```sh
  kubectl delete -f deploy/chaos/experiments/pod-kill-order-service.yaml
  ```

### 2.2 Latency injection (`order-service -> customer-service`, corrected pairing)

- **Manifest**: [`deploy/chaos/experiments/latency-order-to-customer.yaml`](experiments/latency-order-to-customer.yaml)
  (renamed from the task file's original `latency-order-to-payment.yaml` - `order-service <->
  payment-service` is Kafka-only/asynchronous with no circuit breaker to observe; see the
  manifest's own header comment and `STEADY-STATE.md`'s "Corrections to upstream task text" for the
  full derivation).
- **Apply**:
  ```sh
  kubectl apply -f deploy/chaos/experiments/latency-order-to-customer.yaml
  ```
- **Watch**: Grafana dashboard `circuit-breakers`, panel "Circuit Breaker State per Instance"
  (watch the `customer-service`-named breaker transition to `open`), corroborated by "Circuit
  Breaker Calls (success / failed / not-permitted)", at `http://localhost:3000`.
- **Steady-state hypothesis** (summarized from `STEADY-STATE.md` 20.2.1): injecting network delay
  on the `order-service -> customer-service` call does not cascade into upstream failures - once
  injected latency causes enough timeouts to push the failure rate over 50% across the 10-call
  sliding window, the `customer-service`-named breaker on `order-service` opens (via the
  failure-rate path - no `slowCallDurationThreshold` is configured) and calls fail fast instead of
  piling up. Note the manifest's own documented gap: `customerRestClient` has no configured
  connect/read timeout, so this experiment is not guaranteed to reliably trip the breaker via delay
  alone until that timeout is added (tracked as a follow-up for platform-engineer/domain-engineer,
  not fixed by this runbook).
- **Abort criteria**: abort if the breaker does NOT open within the expected sliding window (10
  calls) after latency injection starts, or if `order-service`'s own p95 latency
  (`platform-overview`, "HTTP p95 Latency by Service (s)") breaches 0.3s for longer than 2 minutes
  before the breaker opens.
- **Abort command**:
  ```sh
  kubectl delete -f deploy/chaos/experiments/latency-order-to-customer.yaml
  ```

### 2.3 Network partition (`billing-service` <-> Kafka)

- **Manifest**: [`deploy/chaos/experiments/partition-billing-service-kafka.yaml`](experiments/partition-billing-service-kafka.yaml)
- **Apply**:
  ```sh
  kubectl apply -f deploy/chaos/experiments/partition-billing-service-kafka.yaml
  ```
- **Watch**: Grafana dashboard `kafka-billing-ops`, panel "Kafka Consumer Lag by Group" (primary),
  corroborated by "Bill-Run Duration" (secondary - watch for stalled bill-runs during the
  partition), at `http://localhost:3000`.
- **Steady-state hypothesis** (summarized from `STEADY-STATE.md` 20.2.1): isolating
  `billing-service` from Kafka does not lose messages - consumer lag rises while the partition is
  in effect but drains back down once the partition heals (no `KafkaConsumerLagHigh` sustained past
  recovery), consistent with the outbox/inbox pattern's at-least-once, idempotent recovery
  (ADR-009/019). Cross-check with the outbox row-count comparison documented in the manifest's own
  header comment (`SELECT count(*) FROM outbox_event WHERE created_at >= '<partition-start>'`,
  before vs. after recovery).
- **Abort criteria**: abort if lag has not started draining within 5 minutes of the partition
  healing, or if `KafkaConsumerLagHigh` is still firing 5 minutes after the `NetworkChaos`
  resource is deleted.
- **Abort command**:
  ```sh
  kubectl delete -f deploy/chaos/experiments/partition-billing-service-kafka.yaml
  ```

---

## 3. Post-game-day findings template (20.4.2)

> **UNFILLED / EXAMPLE-ONLY.** No experiment has been run against a live cluster this sprint
> (Docker Desktop was down for the entire Sprint 20 authoring session - see the "Verification
> status" notes in `deploy/chaos/README.md`, `deploy/chaos/STEADY-STATE.md`, and each experiment
> manifest). The rows below are blank/example placeholders only, not real results. Do not treat any
> value in this section as an actual game-day outcome.

Fill in one row per experiment run, immediately after (or during) the game day:

| Date | Operator | Experiment | Hypothesis | Pass/Fail | Alerts fired | Alerts expected-but-not-fired | Manual abort (yes/no + reason) | Follow-up actions + owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| _(example - pod-kill)_ YYYY-MM-DD | _(name)_ | `pod-kill-order-service` | Killing one `order-service` pod does not reduce served traffic or raise p95 past 0.3s (NFR-07); PDB keeps >=1 pod serving. | _(pass/fail)_ | _(e.g. none, or `ApiHighP95Latency`)_ | _(alert-rule column from STEADY-STATE.md: `ApiHighP95Latency`, if expected but did not fire)_ | _(yes/no + reason)_ | _(action - owner)_ |
| _(example - latency injection)_ YYYY-MM-DD | _(name)_ | `latency-order-to-customer` | Injected delay on `order-service -> customer-service` trips the `customer-service` breaker via the failure-rate path within the 10-call sliding window. | _(pass/fail)_ | _(e.g. `CircuitBreakerOpen`)_ | _(alert-rule column from STEADY-STATE.md: `CircuitBreakerOpen`, if expected but did not fire)_ | _(yes/no + reason)_ | _(action - owner, e.g. "add read timeout to customerRestClient - platform-engineer")_ |
| _(example - network partition)_ YYYY-MM-DD | _(name)_ | `partition-billing-service-kafka` | Isolating `billing-service` from Kafka does not lose messages; consumer lag rises then drains after healing, no permanent event loss. | _(pass/fail)_ | _(e.g. `KafkaConsumerLagHigh`)_ | _(alert-rule column from STEADY-STATE.md: `KafkaConsumerLagHigh`, if expected but did not fire)_ | _(yes/no + reason)_ | _(action - owner)_ |

"Alerts fired" / "Alerts expected-but-not-fired" reference the **Alert Rule** column of
[`deploy/chaos/STEADY-STATE.md` Section 20.2.1's mapping table](STEADY-STATE.md#221---steady-state-hypothesis-mapping-table)
(`ApiHighP95Latency`, `CircuitBreakerOpen`, `KafkaConsumerLagHigh`) - record which of those actually
fired in Prometheus/Alertmanager during the run versus which the hypothesis predicted but that did
not fire.

---

## Verification status (this authoring session)

Docker Desktop was down for this authoring session; no cluster was available. This document's
commands have **not been dry-run against a real cluster**. The following is satisfied by
documentation shape alone:

- 20.4.1: each experiment subsection (2.1-2.3) has a copy-pasteable `kubectl apply` command, the
  dashboard/panel to watch reachable via the Section 10 port-forward, the summarized hypothesis,
  and a copy-pasteable abort command; the Prerequisites section introduces no tool beyond
  `deploy/RUNBOOK.md`'s base set. **Satisfied (documentation-shape).**
- 20.4.2: the findings template has a column for date, operator, experiment, hypothesis, pass/fail,
  alerts fired, alerts expected-but-not-fired, manual abort (yes/no + reason), and follow-up
  actions + owner, with one example row per experiment type, explicitly marked unfilled/example
  only. **Satisfied.**

**Not verified this session** (requires a live cluster, tracked as a follow-up once Docker
recovers):

- That the `kubectl apply`/`kubectl delete` commands in Sections 2.1-2.3 actually succeed against
  a real Kind cluster with Chaos Mesh installed.
- That the cited dashboards/panels actually render the described behavior during a real
  experiment run.
- A first live game day should treat this runbook as an unvalidated-in-practice v1 and confirm each
  command works as written before relying on it operationally.
