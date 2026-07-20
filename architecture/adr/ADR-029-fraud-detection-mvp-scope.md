# ADR-029 Fraud Detection MVP Scope (SIM-Swap, Rule-Based)

Status: Accepted
Date: 2026-07-11
Ratified: 2026-07-17 (tech-lead, with Amendments 1-3 below; gates Sprint 23 build work)

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
Note (Amendment 1): `msisdn.released.v1` does not currently carry `customerId`, which
`MSISDN_CHURN_VELOCITY` keys on; see Section 4.

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
  reassigned to a different `subscriptionId`, within a short window (default 15 minutes). (Amendment 2:
  neither event carries a SimCard/ICCID identifier; `subscriptionId` is the only observable
  re-assignment key and is the correct proxy, since MSISDN allocation is bound to a subscription at
  activation.)
- `MSISDN_CHURN_VELOCITY`: more than N (default 3) allocate/release cycles for the same `customerId`
  within a rolling 24-hour window.
- `SUSPEND_REACTIVATE_VELOCITY` (included this phase): unusual `subscription.suspended.v1` /
  `subscription.activated.v1` cycling for the same subscription within a short window. Evaluation
  SHOULD exclude `reason=NON_PAYMENT` suspensions (field present on `subscription.suspended.v1`) to
  suppress legitimate dunning-cycle false positives (Amendment 3).

Data dependency (Amendment 1): `MSISDN_CHURN_VELOCITY` keys on `customerId`, but `msisdn.released.v1`
does not currently carry it. This ADR requires adding `customerId` to `msisdn.released.v1` as a
BACKWARD-compatible nullable union (`["null","string"]`, default null), populated by
subscription-service's `TerminateSubscriptionCommandHandler` from `subscription.getCustomerId()`
(already in scope) - mirroring the `orderId` nullable-union precedent in `subscription.activated.v1`
and the `customerId` addition to `quota.threshold-reached.v1` (event-catalog.md 2026-07-04). This
producer change is a prerequisite subtask of Sprint 23 (Feature 23.2). As defensive resilience,
fraud-service SHOULD also resolve a release's `customerId` by joining to the most recent prior
`MSISDN_ALLOCATED` signal for the same `msisdn` in `MsisdnLifecycleSignal` when the field is absent
(covering events published before the field landed); releases with no known prior allocation are
excluded from the velocity count.

### 5. Response model: detect and alert, no automated hold by default

ADVANCED.md Section 4.4's "high-risk actions step up auth or hold" is explicitly **not** implemented
automatically in this phase. fraud-service publishes `fraud.signal-raised.v1` (every rule hit) and
`fraud.case-opened.v1` (escalated cases); ticket-service consumes `fraud.case-opened.v1` and
auto-opens a linked ticket for agent review, reusing ticket-service's existing `OpenTicketCommandHandler`
and `SlaPolicy` machinery via a new inbox consumer (the pattern ADR-028 also specifies for
dispute-service; both consumers are new work - ticket-service has no event consumer today) rather than
fraud-service reinventing case-management workflow. Any account suspension remains a manual agent action via
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
