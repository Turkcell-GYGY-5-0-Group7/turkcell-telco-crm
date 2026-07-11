# ADR-029 Fraud Detection MVP Scope (SIM-Swap, Rule-Based)

Status: Proposed
Date: 2026-07-11

---

## Context

TELCO-CRM-ADVANCED.md Section 4.4 proposes "real-time fraud scoring on the charging/order path
(velocity checks, SIM-swap detection, unusual top-up/usage patterns) fed by the streaming platform;
high-risk actions step up auth or hold." "The streaming platform" refers to the lakehouse/Flink/ML
data-and-intelligence platform of ADVANCED.md Section 5, which does not exist yet and is itself phase
P10 in the adoption roadmap (ADVANCED.md Section 10). There is also no `charging-service`/OCS in the
MVP (ADVANCED.md Section 2.1 is separately deferred), so there is no real-time charging path to hook
into.

The platform does, however, already publish the domain events needed to detect the most common form
of SIM-swap abuse - rapid MSISDN release/reallocation and unusual subscription-suspend/reactivate
cycling - via `msisdn.allocated.v1`, `msisdn.released.v1`, `subscription.activated.v1`, and
`subscription.suspended.v1` (all produced by subscription-service today, per `event-catalog.md`).

## Decision

### This ADR explicitly narrows and supersedes the streaming-based approach of TELCO-CRM-ADVANCED.md Section 4.4, for this initial phase only

This ADR authorizes a **pragmatic, rule-based MVP** for SIM-swap / fraud detection, reacting to
existing Kafka domain events. It explicitly does **not** authorize the streaming/lakehouse/ML version
described in ADVANCED.md Section 4.4 and Section 5. That full version remains the eventual target and
requires its own, later ADR once the data-and-intelligence platform (ADVANCED.md Section 5, phase P10)
exists. Nothing in this ADR overrides ADVANCED.md's long-term direction; it only defines what is built
now, per the root CLAUDE.md's ADR-precedence rule.

### 1. Service boundary

A new, lightweight **fraud-service** (proposed port 9013) is created; fraud-detection logic is **not**
added to subscription-service.

Rationale: subscription-service's bounded context is executing subscription/MSISDN/SimCard lifecycle
transitions correctly, atomically, and fast (audit-mandated, CQRS + Mediator, a tightly-scoped state
machine per its own CLAUDE.md). Rule evaluation over a rolling time window (velocity counters,
cross-referencing allocate/release pairs across customers) is a distinct concern with a different
scaling and retention profile; adding it to subscription-service would blur that service's bounded
context and couple an operational, latency-sensitive service to a security-analytics workload.
fraud-service is read-only relative to subscription-service: it consumes subscription-service's
already-published events via the inbox and never accesses `subscription-db` directly (ADR-006
cross-service data rule). This also matches the eventual target state already named in
TELCO-CRM-ADVANCED.md Section 6 (`fraud-service` listed as a "New" service) - the same reasoning
already applied to the campaign-service decision (ADR-027).

### 2. Architecture mode (ADR-004)

**CQRS + Mediator.** Structurally identical to usage-service: consume domain events via the inbox,
evaluate threshold-based rules, publish signal events via the outbox. Business rules exist
(admin-configurable thresholds per rule code), domain logic is non-trivial, events are emitted. No
saga/compensation or multi-service write coordination is owned by fraud-service, so Domain
Orchestration is not warranted.

### 3. Data ownership (ADR-006)

fraud-service owns a new `fraud-db` (PostgreSQL 17, database-per-service) - mandatory relational
primary store because fraud-service emits domain events (ADR-006's event-emitting-services rule).
Redis MAY be added as a cache for hot per-customer velocity counters, explicitly not as the source of
truth. Aggregates: `MsisdnLifecycleSignal` (raw ingested event log for rolling-window queries),
`FraudRule` (rule code + configurable threshold/window), `FraudSignal` (an evaluated rule hit),
`FraudCase` (one or more related signals escalated into an actionable case).

### 4. Rules (MVP scope)

A fixed, small set of rule *codes* with admin-configurable thresholds - not a boolean-expression rule
engine (that would be over-engineering for this phase):

- `RAPID_SIM_SWAP`: `msisdn.released.v1` followed by `msisdn.allocated.v1` for the same MSISDN,
  reassigned to a different SimCard/subscription, within a short window (default 15 minutes).
- `MSISDN_CHURN_VELOCITY`: more than N (default 3) allocate/release cycles for the same `customerId`
  within a rolling 24-hour window.
- `SUSPEND_REACTIVATE_VELOCITY` (second priority): unusual `subscription.suspended.v1` /
  `subscription.activated.v1` cycling for the same subscription within a short window.

### 5. Response model: detect and alert, no automated hold by default

ADVANCED.md Section 4.4's "high-risk actions step up auth or hold" is explicitly **not** implemented
automatically in this phase. fraud-service publishes `fraud.signal-raised.v1` (every rule hit) and
`fraud.case-opened.v1` (escalated cases); ticket-service consumes `fraud.case-opened.v1` and
auto-opens a linked ticket for agent review, reusing its existing SLA/assignment machinery (the same
reuse pattern used by ADR-028's dispute-service -> ticket-service integration) rather than fraud-service
reinventing case-management workflow. Any account suspension remains a manual agent action via
subscription-service's existing `POST /api/v1/subscriptions/{id}/suspend` endpoint. Automating the
hold is deferred until detection precision is proven, to avoid false-positive-driven customer harm.

### 6. Deferred to a later ADR

The full streaming/lakehouse/ML version of fraud detection - real-time scoring on a charging path that
does not yet exist, velocity checks fed by Flink/streaming analytics, ML-based anomaly scoring, and
automated step-up-auth/hold actions - is deferred. A later ADR is required once ADVANCED.md Section 5
(data-and-intelligence platform) and a charging-service (ADVANCED.md Section 2.1) exist.

## Consequences

### Positive

- Delivers real SIM-swap detection value now, using only infrastructure that already exists (existing
  Kafka events, existing outbox/inbox pattern, existing ticket-service SLA machinery).
- No premature investment in a streaming/ML platform before the data platform exists.
- Detect-and-alert-only default avoids false-positive-driven customer harm from automated holds.

### Negative

- Detection is reactive (event-driven, near-real-time) rather than truly real-time / pre-transaction,
  since there is no charging-service hot path to intercept in the MVP.
- Rule thresholds are hand-tuned, not ML-scored; expect a higher false-positive/false-negative rate
  than the eventual ML version.

## Alternatives Considered

### Fraud detection inside subscription-service

Rejected - blurs subscription-service's operational bounded context with a security-analytics
workload; see Decision Section 1.

### Build the full ADVANCED.md Section 4.4 streaming/ML version now

Rejected - the required lakehouse/Flink/ML platform and charging-service do not exist; building this
now would be premature and unsupportable.

### A full rule-expression engine (DSL) for arbitrary fraud rules

Rejected for this phase - over-engineering relative to the small, fixed rule set needed; a
parameterized-threshold `FraudRule` aggregate is sufficient.

## Related ADRs

* ADR-004 Architecture Style (mode selection: CQRS + Mediator)
* ADR-006 Database Strategy (database-per-service, event-emitting services relational rule)
* ADR-009 Event Driven Architecture / ADR-019 Event Contract and Schema Governance
* ADR-017 Service Template Standard
