# Sprint 20 - Chaos Engineering (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). It is documented now
> and built later. Feature subtask files will be authored when the sprint is scheduled.

## Objective

Introduce fault-injection and game-day capability against the platform's proven Kind/Helm/Kubernetes
deployment target (Sprint 15), so that resilience claims that today rest on unit/integration tests
(Sprint 13 Resilience4j circuit breaker/retry/bulkhead) are also validated by deliberately breaking
the running system and observing that it self-heals within the established SLOs.

This sprint delivers `docs/product/TELCO-CRM-ADVANCED.md` Section 3.4's "Chaos engineering in
staging" bullet: fault injection (latency, partition, instance kill) with steady-state hypotheses,
and game days. It is a **narrower, single-cluster-appropriate slice** of the larger Phase P9 item in
Section 10 ("cells, multi-region, DR drills, chaos engineering") - this sprint targets the existing
single Kind/K8s cluster and the services already deployed to it; multi-region cells, cross-region DR
drills, and RPO/RTO targets (Section 3.4's DR bullet) are explicitly out of scope and remain later
P9 work.

Per a tech-lead ruling for this effort, this sprint does **not** introduce a new ADR. It is scoped as
an extension of two existing decisions: ADR-012 (Observability Strategy - the dashboards, metrics,
and alerting a chaos experiment observes) and ADR-013 (Testing Strategy - chaos experiments are a
new rung on the existing test pyramid, exercised deliberately rather than continuously). See
References.

## Included Epics

- Epic 20: Chaos Engineering (fault injection + game days)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 20.1 | Fault-injection tool selection and cluster install | TODO | [20.1-fault-injection-tool-selection-and-cluster-install.md](20.1-fault-injection-tool-selection-and-cluster-install.md) |
| 20.2 | Steady-state hypotheses and observability wiring | TODO | [20.2-steady-state-hypotheses-and-observability-wiring.md](20.2-steady-state-hypotheses-and-observability-wiring.md) |
| 20.3 | Experiment library: pod-kill, latency injection, network partition | TODO | [20.3-experiment-library-pod-kill-latency-injection-network-partition.md](20.3-experiment-library-pod-kill-latency-injection-network-partition.md) |
| 20.4 | Game-day runbook | TODO | [20.4-game-day-runbook.md](20.4-game-day-runbook.md) |
| 20.5 | Blast-radius guardrails and namespace scoping | TODO | [20.5-blast-radius-guardrails-and-namespace-scoping.md](20.5-blast-radius-guardrails-and-namespace-scoping.md) |

### Feature notes

- **20.1 Fault-injection tool selection and cluster install.** Recommendation: **Chaos Mesh**, not
  Litmus and not hand-rolled `kubectl`/`tc`/`iptables` scripts. Chaos Mesh is Kubernetes-native
  (CRDs: `PodChaos`, `NetworkChaos`, `StressChaos`, `Schedule`, `Workflow`), installs via a single
  Helm chart alongside the existing `deploy/helm/dependencies` pattern (ADR-014, one-chart-per-
  concern precedent), requires no changes to service images or Dockerfiles (a cluster-level
  controller + daemonset injects faults, nothing is baked into `microservices/*`), and covers all
  three required experiment types (instance kill, latency, network partition) out of the box.
  Litmus was considered and rejected for this scope: its ChaosEngine/ChaosExperiment/ChaosResult
  CRD model plus the separate Litmus Portal web app add operational surface this single-cluster
  slice does not need. Manual `kubectl delete pod` / `tc qdisc` scripts were considered and rejected
  as the long-term mechanism: pod-kill alone is trivial with `kubectl`, but latency and partition
  injection need per-node `tc`/`iptables` orchestration that Chaos Mesh already solves safely and
  repeatably, and ad hoc scripts have no built-in steady-state comparison or scheduling. Install
  scoped to the `telco` namespace on the same Kind cluster proven in Sprint 15 (`deploy/RUNBOOK.md`
  Section 2), gated so experiments never run in a namespace tagged `production`.
- **20.2 Steady-state hypotheses and observability wiring.** Reuse, do not duplicate, the Sprint 13
  Grafana dashboards as the sole observability surface for chaos: `platform-overview` (p95/p99
  latency, error rate, throughput), `kafka-billing-ops` (consumer lag, bill-run duration), and
  `circuit-breakers` (breaker state transitions). Each experiment in 20.3 states a steady-state
  hypothesis in terms of an existing panel/alert threshold (e.g. "p95 latency on `order-service`
  stays under its NFR-07 budget" or "no circuit breaker opens on `billing-service` dependents"), not
  a new metric. No new dashboards or alerts are introduced by this sprint.
- **20.3 Experiment library: pod-kill, latency injection, network partition.** Three experiment
  types, each defined as a Chaos Mesh CRD manifest under a new `deploy/chaos/` directory (mirroring
  `deploy/helm/`, `deploy/kind/` layout): (a) pod-kill (`PodChaos`, kill mode) against a domain
  service pod to verify the PDB (`minAvailable: 1`) and HPA (Sprint 15.3) keep the service serving
  and the killed pod is rescheduled; (b) latency injection (`NetworkChaos`, delay) on an outbound
  call between two services already guarded by Resilience4j (Sprint 13.4, e.g.
  order-service -> payment-service) to verify the circuit breaker opens/half-opens as designed
  instead of cascading; (c) network partition (`NetworkChaos`, partition) isolating a service from
  Kafka to verify the outbox/inbox pattern (ADR-009/019) recovers without message loss once the
  partition heals.
- **20.4 Game-day runbook.** A `deploy/chaos/GAMEDAY-RUNBOOK.md`, parallel in spirit to
  `deploy/RUNBOOK.md` (Sprint 15.5): prerequisites, how to run each 20.3 experiment against the Kind
  cluster, where to watch (the three dashboards from 20.2), the steady-state hypothesis and abort
  criteria for each experiment, and a post-game-day template (what broke, what alerted, what did
  not, follow-up actions). Written so a single operator can run a game day end to end, the same way
  `deploy/RUNBOOK.md` lets one operator deploy end to end.
- **20.5 Blast-radius guardrails and namespace scoping.** Chaos Mesh RBAC and `Schedule`
  configuration restricted to the `telco` namespace; experiments require an explicit manual
  `kubectl apply` (no scheduled/automatic chaos in this sprint - that is a later game-day-maturity
  step); every experiment manifest carries a bounded `duration` and a documented manual-abort command
  (`kubectl delete -f <experiment>.yaml`); CI is untouched by this sprint (chaos does not run in the
  build/deploy gate, ADR-014's CI gate is unaffected).

## Sprint Deliverables

- Chaos Mesh installed into the `telco` namespace on the existing Kind cluster via a Helm release
  under `deploy/chaos/`.
- A documented steady-state hypothesis per experiment, expressed against the existing
  `platform-overview` / `kafka-billing-ops` / `circuit-breakers` Grafana dashboards - no new
  dashboards.
- Three experiment manifests (pod-kill, latency injection, network partition) covering at least one
  domain service each, runnable on demand against the Kind cluster.
- `deploy/chaos/GAMEDAY-RUNBOOK.md` describing how to plan, run, observe, and abort a game day, plus
  a post-game-day findings template.

## Exit Criteria

- A pod-kill experiment against a domain service shows the PDB/HPA keep the service serving and the
  pod reschedules, matching Sprint 15's live-verified HPA/PDB behavior.
- A latency-injection experiment against a Resilience4j-guarded call shows the breaker opens per
  Sprint 13.4's design rather than the fault cascading upstream.
- A network-partition experiment against Kafka shows the outbox/inbox pattern recovers with no
  message loss once the partition heals.
- Every experiment's steady-state hypothesis and result is observable on an existing Sprint 13
  dashboard - no experiment requires a new metric or panel to be interpreted.
- A single operator can run a full game day by following `deploy/chaos/GAMEDAY-RUNBOOK.md` alone.

## References

- [docs/product/TELCO-CRM-ADVANCED.md](../../product/TELCO-CRM-ADVANCED.md) Section 3.4 (Resilience
  Engineering - source bullet for this sprint) and Section 10 (Adoption Roadmap, Phase P9 - this
  sprint is a narrower, single-cluster slice of that phase's full multi-region/DR/chaos scope).
- [ADR-012 Observability Strategy](../../../architecture/adr/ADR-012-observability-strategy.md) -
  this sprint extends it (chaos experiments observe via the existing Prometheus/Grafana/Tempo/Loki
  stack); no ADR change or new ADR is introduced.
- [ADR-013 Testing Strategy](../../../architecture/adr/ADR-013-testing-strategy.md) - this sprint
  extends the test pyramid with deliberate, on-demand fault injection; it does not replace or
  supersede the existing unit/integration/contract/E2E layers.
- [Sprint 13 - Observability and Resilience](../sprint-13-observability-and-resilience/README.md) -
  the Resilience4j circuit-breaker/retry/bulkhead work (13.4) this sprint validates under real
  faults, and the three Grafana dashboards (13.3) this sprint reuses as its observability surface.
- [deploy/RUNBOOK.md](../../../deploy/RUNBOOK.md) - the Kind/Helm deployment target chaos
  experiments run against; `deploy/chaos/GAMEDAY-RUNBOOK.md` (20.4) is a parallel operational
  document for game days.
- [deploy/helm/README.md](../../../deploy/helm/README.md) - the chart layout/install pattern
  `deploy/chaos/`'s Chaos Mesh Helm release follows.
