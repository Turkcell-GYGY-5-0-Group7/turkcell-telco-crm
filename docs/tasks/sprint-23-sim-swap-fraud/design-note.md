# Design Note: SIM-Swap / Fraud Detection (MVP scope)

| Field | Value |
| --- | --- |
| Track | Sprint 23 |
| Status | Draft (input to ADR-029, Proposed) |
| Author | architecture agent |
| Last updated | 2026-07-11 |
| Related | [ADR-029](../../../architecture/adr/ADR-029-fraud-detection-mvp-scope.md), [service-catalog.md](../../architecture/service-catalog.md), [event-catalog.md](../../architecture/event-catalog.md) |

## 1. Explicit scope-down (read this first)

TELCO-CRM-ADVANCED.md Section 4.4 proposes "real-time fraud scoring on the charging/order path
(velocity checks, SIM-swap detection, unusual top-up/usage patterns) fed by the streaming platform,"
where "the streaming platform" is the lakehouse/Flink/ML platform of ADVANCED.md Section 5 - which
does not exist and is itself phase P10 in the adoption roadmap (ADVANCED.md Section 10). That version
is **explicitly deferred**.

This track builds the MVP-appropriate subset only: **rule-based detection reacting to existing Kafka
domain events**, specifically the MSISDN allocation/release and subscription lifecycle events that
already exist in `event-catalog.md`. No streaming engine, no lakehouse, no ML scoring, no real-time
charging-path hook (there is no charging-service in the MVP; ADVANCED.md Section 2.1 is also
deferred). See ADR-029 for the formal narrowing/superseding statement.

## 2. Where does this live?

**Option A - inside subscription-service.** subscription-service already owns the SimCard/MsisdnPool
aggregates closest to a SIM-swap signal, and already publishes `msisdn.allocated.v1` /
`msisdn.released.v1`.

**Option B - new lightweight fraud-service consuming subscription/identity events (chosen).**

Rationale: subscription-service's job is executing lifecycle transitions correctly, atomically, and
fast (it is audit-mandated and CQRS + Mediator with a tight, well-defined state machine per its
CLAUDE.md). Rule evaluation over a rolling time window (velocity counters, cross-referencing
allocate/release pairs) is a distinct concern with a different scaling and retention profile than
executing a single MSISDN state transition - bolting it onto subscription-service would blur that
service's bounded context and couple an operational, latency-sensitive service to a
security-analytics workload. A separate fraud-service also matches the eventual target state already
named in TELCO-CRM-ADVANCED.md Section 6 (`fraud-service` listed as a "New" service), so this ADR
builds the same service narrower now rather than starting inside subscription-service and extracting
it later - the same pattern used for the campaign-service decision in Track 1.

fraud-service is **read-only relative to subscription-service**: it consumes subscription-service's
already-published events via the inbox and never accesses `subscription-db` directly (ADR-006).

**Decision: new `fraud-service`** (proposed port 9013).

## 3. Architecture mode (ADR-004)

**CQRS + Mediator.** Structurally identical to usage-service: consume domain events (inbox), evaluate
rules/thresholds, publish signal events (outbox). Business rules exist (configurable thresholds),
domain logic is non-trivial, events are emitted. No saga/compensation or multi-service write
coordination is owned by fraud-service, so Domain Orchestration is not warranted.

## 4. Rules (MVP: parameterized thresholds, not a rule-expression engine)

A `FraudRule` aggregate holds a small, fixed set of rule *codes* with admin-configurable thresholds
(not a boolean-expression DSL - that would be over-engineering for the MVP-appropriate scope):

1. **RAPID_SIM_SWAP** - an `msisdn.released.v1` followed by an `msisdn.allocated.v1` for the same
   MSISDN (reassigned to a different `subscriptionId` - neither event carries a SimCard/ICCID
   identifier, so `subscriptionId` is the observable re-assignment key; ADR-029 Amendment 2) within a
   short window (default 15 minutes) -> raise a signal, severity HIGH.
2. **MSISDN_CHURN_VELOCITY** - more than N (default 3) allocate/release cycles for the same
   `customerId` within a rolling 24-hour window -> raise a signal, severity MEDIUM.
3. **SUSPEND_REACTIVATE_VELOCITY** (second priority, include if straightforward) - unusual
   `subscription.suspended.v1`/`subscription.activated.v1` cycling for the same subscription within a
   short window -> raise a signal, severity LOW/MEDIUM.

Thresholds (window minutes, count) are stored per rule code so ops can tune without a redeploy; the
rule *codes* themselves are fixed at this MVP stage (adding a genuinely new rule type is still a code
change).

## 5. Response model - detect and alert only (no automated hold by default)

ADVANCED.md Section 4.4 says "high-risk actions step up auth or hold." This MVP **deliberately
narrows** that to detection and alerting only:

- Every rule hit publishes `fraud.signal-raised.v1` (informational).
- Repeated/high-severity signals for the same customer escalate to a `FraudCase`, publishing
  `fraud.case-opened.v1`.
- ticket-service consumes `fraud.case-opened.v1` and auto-opens a linked ticket for agent review,
  reusing its existing SLA/assignment machinery (same reuse pattern as Track 2's dispute-service ->
  ticket-service integration).
- notification-service consumes `fraud.case-opened.v1` for an internal ops/security alert.
- **No automated subscription suspension is triggered by fraud-service in the MVP.** An agent
  reviewing the auto-opened ticket may manually call subscription-service's existing
  `POST /api/v1/subscriptions/{id}/suspend` if warranted. Automating that hold is explicitly deferred
  until detection precision is proven (avoiding false-positive-driven customer harm) - this deferral,
  and the full streaming/ML version, require a later ADR per ADR-029.

## 6. Aggregate shapes

```text
MsisdnLifecycleSignal (fraud-service, fraud-db)
  id, eventType (MSISDN_ALLOCATED | MSISDN_RELEASED | SUBSCRIPTION_SUSPENDED | SUBSCRIPTION_ACTIVATED)
  customerId, msisdn, subscriptionId, occurredAt, ingestedAt
  -- raw ingested event log; rolling-window queries run against this table (pruned periodically);
     Redis MAY cache hot per-customer counters as a non-source-of-truth accelerator (ADR-006).

FraudRule
  code (RAPID_SIM_SWAP | MSISDN_CHURN_VELOCITY | SUSPEND_REACTIVATE_VELOCITY)
  windowMinutes, thresholdCount, severity, enabled

FraudSignal
  id, ruleCode, customerId, msisdn, subscriptionId, severity, triggeredAt
  sourceSignalIds (references into MsisdnLifecycleSignal)

FraudCase
  id, customerId, status (OPEN | UNDER_REVIEW | CONFIRMED | DISMISSED)
  signalIds (references into FraudSignal), openedAt, resolvedAt (nullable), resolvedBy (nullable)
```

## 7. Events

Consumed (existing, from subscription-service via inbox): `msisdn.allocated.v1`,
`msisdn.released.v1`, `subscription.activated.v1`, `subscription.suspended.v1`.

Published (new, from fraud-service via outbox): `fraud.signal-raised.v1`, `fraud.case-opened.v1`,
`fraud.case-resolved.v1`. Consumers: ticket-service (auto-ticket), notification-service (ops alert).

## 8. Data ownership (ADR-006)

fraud-service owns `fraud-db` (PostgreSQL 17, mandatory since fraud-service emits domain events - no
MongoDB exception applies here). Redis MAY be added as a cache for hot velocity counters, explicitly
not as the source of truth, consistent with the platform's Redis usage rule.
