**TELCO CRM PLATFORM - ADVANCED / ENTERPRISE EVOLUTION**

_Senior-level design beyond the MVP_

Version 1.0 (English)

---

> **Purpose.** [`TELCO-CRM-MVP.md`](TELCO-CRM-MVP.md) delivers a correct, observable, event-driven
> subscriber lifecycle. This document defines how that MVP grows into a production-grade,
> enterprise-scale telco platform. It (1) fleshes out the MVP Scope-Out items (MVP Section 6.2), and
> (2) adds professional concerns the MVP intentionally omitted: hyperscale and resilience, zero-trust
> security and compliance, and a data/intelligence platform.
>
> **Status.** This is a forward-looking design (program phases P6+). It is not yet in the delivery
> backlog. Items that graduate into delivery become sprints in [`docs/tasks/`](../tasks/) and, where
> they change a platform decision, a new ADR in `architecture/adr/`. Nothing here overrides an
> existing ADR without a superseding ADR.

---

# Table of Contents

1. Guiding Principles
2. Scope-Out Features, Designed Out
3. Scale and Resilience
4. Security and Compliance
5. Data and Intelligence Platform
6. New and Evolved Services
7. Target Architecture (post-MVP)
8. Non-Functional Uplift Targets
9. Proposed ADRs
10. Adoption Roadmap (P6+)

---

# 1. Guiding Principles

- **Evolve, do not rewrite.** Every capability here layers onto the existing platform-core, starters,
  and event backbone. New services follow ADR-017; new events follow ADR-009/019.
- **Boring where it counts.** Prefer proven patterns (CDC, outbox, idempotency, cell isolation) over
  novelty. Novelty is reserved for differentiating intelligence (rating, churn, NBA).
- **Compliance is a first-class feature, not a bolt-on.** KVKK/GDPR data lifecycle is designed into
  the data model, not patched later.
- **Every capability is measurable.** No feature ships without its SLOs, dashboards, and alerts.

---

# 2. Scope-Out Features, Designed Out

Each MVP Section 6.2 deferral, taken to an implementable design.

## 2.1 Prepaid and Real-Time Charging (OCS)

The MVP bills postpaid in monthly batches. Prepaid requires **real-time charging**: a balance must be
checked and decremented *before* or *during* a session, in milliseconds.

- New **charging-service** (Online Charging System, OCS) implementing a Diameter Gy-style credit
  control loop (initial / update / terminate) against a low-latency balance store (Redis + durable
  PostgreSQL/ledger).
- Reservation model: reserve quota for an active session, settle on session end, release on failure.
- Top-up flow: `wallet.topped-up.v1` credits balance atomically; integrates with payment-service.
- Rating moves from monthly batch to a streaming **rating engine** (Section 5.3) shared by pre/postpaid.

> Trade-off: real-time charging trades the MVP's eventual-consistency comfort for strict, low-latency
> consistency on the balance hot path. Isolate it (its own datastore, its own cell) so it cannot be
> destabilised by slower domains.

## 2.2 Mobile Number Portability (MNP)

FR-16, scaffolded in the MVP. Full design:

- A dedicated **porting state machine** (PortIn / PortOut) coordinating with the national porting
  clearinghouse (NPC) via an anti-corruption adapter.
- States: REQUESTED -> VALIDATED -> SCHEDULED -> ACTIVATED / REJECTED / CANCELLED, each with SLA
  timers and compensation.
- Events: `portin.requested.v1`, `portin.activated.v1`, `portout.completed.v1`. Subscription-service
  consumes activation to swap MSISDN ownership without dropping the subscription.

## 2.3 Corporate Customers and Fleet Management

- Extend the Customer aggregate with a **CorporateAccount** hierarchy: legal entity -> cost centers
  -> employee lines, with VKN and e-invoice (e-Fatura) identifiers.
- Fleet billing: consolidated invoices, per-line policies (spend caps, allowed services), pooled
  quota shared across lines.
- Delegated administration: a corporate admin role managing their own fleet via RBAC scopes.

## 2.4 Campaign and Promotion Engine

- New **campaign-service** with a rule engine (eligibility, targeting segments, validity windows) and
  a discount/benefit model applied at rating time.
- Segments are computed from the data platform (Section 5) and published as `segment.membership.v1`.
- A/B and holdout groups for measurable uplift; campaign performance flows back into analytics.

## 2.5 Regulatory Reporting (BTK / e-Fatura)

- A **reporting-service** producing scheduled regulatory extracts (subscriber base, MNP, complaints
  SLA) from the data warehouse, with signed, immutable, retained outputs.
- e-Fatura / e-Arsiv integration in billing-service via a GIB adapter.

## 2.6 Roaming

- Ingest TAP/RAP roaming CDRs through the same usage pipeline with a roaming-partner dimension and
  inter-operator settlement rating; surface roaming spend in real time for bill-shock protection.

## 2.7 Channels: Mobile App, Web, BFF

- Introduce **Backend-for-Frontend (BFF)** services per channel (mobile, web, dealer, call-center)
  composing downstream APIs, so domain services stay channel-agnostic.
- GraphQL or aggregated REST at the BFF; push notifications and websockets for real-time balance.

---

# 3. Scale and Resilience

## 3.1 Multi-Region, Cell-Based Architecture

- Partition subscribers into **cells** (shards) by subscriber key; each cell is a full, isolated stack
  (services + datastores). A cell failure affects only its subscribers (blast-radius control).
- Active-active across regions for read paths; active-passive with controlled failover for
  strongly-consistent write paths (charging, payment).
- A thin **cell router** at the edge maps subscriber -> cell.

## 3.2 Data Scaling

- PostgreSQL: per-service partitioning (range/hash) on high-volume tables (usage, CDR, invoice
  lines); archival tiering of cold partitions to object storage.
- Read scaling: CQRS read models materialised per query shape, served from Redis/Elasticsearch,
  rebuildable by **event replay** from Kafka (retention + compacted topics + a replay tool).
- Kafka: topic partitioning by subscriber key for ordering; tiered storage for long retention.

## 3.3 Backpressure and Flow Control

- Bound every consumer (max in-flight, lag-based autoscaling on consumer lag, not just CPU).
- Dead-letter topics with replay tooling; poison-message quarantine.
- Rate limiting and load shedding at the gateway and at the OCS hot path.

## 3.4 Resilience Engineering

- Resilience4j everywhere (already in MVP) plus **bulkhead isolation per dependency** and graceful
  degradation modes (e.g. serve cached catalog when catalog is down).
- **Chaos engineering** in staging: fault injection (latency, partition, instance kill) with
  steady-state hypotheses; game days.
- **DR targets**: define RPO/RTO per domain (e.g. charging RPO ~0 / RTO minutes; reporting RPO hours).
  Backup/restore drills and cross-region replication validated quarterly.

## 3.5 Performance Hardening

- p99 (not just p95) SLOs on customer-facing paths; latency budgets per hop.
- Async, non-blocking I/O (Reactor/virtual threads) on fan-out heavy services (gateway, BFF, usage).
- Continuous load and soak testing in CI for the bill-run and charging paths.

---

# 4. Security and Compliance

## 4.1 Zero-Trust and Service Mesh

- Replace gateway-behind-trust (MVP) with **mTLS everywhere** via a service mesh (Istio/Linkerd):
  every service-to-service call is mutually authenticated and authorized (SPIFFE/SPIRE identities).
- Fine-grained authz with policy-as-code (OPA/Cedar); least-privilege network policies per namespace.

## 4.2 KVKK / GDPR Data Lifecycle

- **Consent management**: a consent-service recording opt-in/opt-out per purpose and channel; every
  processing decision checks consent.
- **Data Subject Access Requests (DSAR)**: automated export and **right-to-erasure** orchestrated
  across services via a `data-subject.erasure-requested.v1` saga (crypto-shredding for data that
  cannot be hard-deleted due to legal retention).
- **Retention policies** per data class, enforced by scheduled jobs; legal hold overrides.
- **Data residency**: keep PII within jurisdiction; cell/region placement honors residency.

## 4.3 Cryptography and Secrets

- **Tokenization / HSM**: card data and TCKN tokenized; raw values never leave a vault boundary;
  keys in an HSM/KMS with rotation and envelope encryption (the MVP's AES-GCM keys move into KMS).
- Centralized secrets (Vault) with dynamic, short-lived credentials; no static DB passwords.

## 4.4 Fraud Detection and Abuse Prevention

- Real-time fraud scoring on the charging/order path (velocity checks, SIM-swap detection, unusual
  top-up/usage patterns) fed by the streaming platform; high-risk actions step up auth or hold.

## 4.5 Audit, SIEM, and Supply Chain

- Expand the MVP audit log into a tamper-evident, centrally shipped audit stream feeding a **SIEM**;
  security alerting and anomaly detection.
- **Supply-chain security**: SBOM generation, dependency and container scanning, image signing
  (cosign), and provenance (SLSA) gated in CI.

---

# 5. Data and Intelligence Platform

## 5.1 Lakehouse and CDC

- Stream all domain events and database changes (Debezium CDC, already used for the outbox) into a
  **lakehouse** (object storage + Iceberg/Delta) feeding a warehouse for BI and ML.
- Medallion layering (bronze/silver/gold); a governed semantic layer and data catalog with lineage.

## 5.2 Streaming Analytics

- Kafka Streams / Flink jobs for real-time aggregates: live ARPU, consumer lag dashboards, quota
  burn-down, fraud signals, and campaign uplift - all without hitting OLTP stores.

## 5.3 Real-Time Rating Engine

- A shared rating engine (used by OCS prepaid and postpaid overage) turning rated events into
  monetary charges via versioned rate plans; deterministic, replayable, and auditable.

## 5.4 ML Use Cases

- **Churn prediction**: features from usage, billing, tickets, and NPS; scored daily; high-risk
  subscribers routed to retention campaigns.
- **Next-Best-Action / recommendation**: tariff and addon recommendations at the BFF, served by a
  low-latency feature store + model service.
- **AIOps**: anomaly detection on metrics/logs/traces for faster incident detection and noise
  reduction.

## 5.5 FinOps

- Cost attribution per service/cell/team; unit economics (cost per subscriber, per bill-run);
  autoscaling and rightsizing guided by cost + SLO, not just utilization.

---

# 6. New and Evolved Services

| Service | Type | Responsibility |
| --- | --- | --- |
| charging-service (OCS) | New | Real-time prepaid charging, balance reservation, top-up settlement. |
| rating-service | New | Shared rating of usage/sessions into monetary charges. |
| campaign-service | New | Eligibility/targeting rule engine, discounts, A/B. |
| consent-service | New | Purpose-based consent; powers DSAR and erasure. |
| reporting-service | New | Regulatory and e-invoice extracts from the warehouse. |
| porting-service | New | MNP PortIn/PortOut state machine + NPC adapter. |
| bff-* (mobile/web/dealer/care) | New | Channel-specific API composition. |
| fraud-service | New | Real-time fraud scoring and holds. |
| analytics/ml-platform | New | Lakehouse, streaming jobs, feature store, model serving. |
| customer-service | Evolved | Corporate account + fleet hierarchy. |
| subscription-service | Evolved | MNP-aware MSISDN ownership, multi-line pooling. |
| billing-service | Evolved | Consolidated/fleet invoices, e-Fatura, roaming settlement. |
| usage-service | Evolved | Roaming CDR ingestion, real-time balance feed to OCS. |

---

# 7. Target Architecture (post-MVP)

```text
              +----------------------------+
   clients -> |  BFF (mobile/web/dealer)   | -> edge mesh (mTLS, OPA)
              +----------------------------+
                          |
        +-----------------+------------------------------+
        |        domain services (cellular, sharded)     |
        |  identity customer catalog order subscription  |
        |  usage billing payment notification ticket     |
        |  + charging rating campaign porting consent     |
        |    reporting fraud                              |
        +-----------------+------------------------------+
                          |  outbox / CDC (Debezium)
                     [ Kafka Bus ] -- tiered storage, replay
                          |
        +-----------------+------------------------------+
        |     streaming (Flink/KStreams) + lakehouse     |
        |  warehouse  feature store  model serving  SIEM |
        +------------------------------------------------+

Cross-cutting: service mesh (mTLS/SPIFFE), Vault/HSM/KMS, OTel->Tempo,
Loki, Prometheus/Grafana, OPA policy, FinOps cost attribution.
```

---

# 8. Non-Functional Uplift Targets

| Dimension | MVP | Advanced target |
| --- | --- | --- |
| Latency | p95 < 300 ms | p99 < 300 ms; charging hot path p99 < 50 ms |
| Availability | 99.5% | 99.95%+ via cells + multi-region |
| Bill-run | 100K < 30 min | 10M+ subscribers, partitioned/parallel, < 2 h |
| Charging | n/a (batch) | Real-time, < 50 ms credit-control, RPO ~0 |
| Security | gateway JWT, AES-GCM PII | mTLS mesh, tokenization/HSM, zero-trust, SIEM |
| Compliance | audit log | Consent, DSAR, erasure saga, residency, retention |
| DR | none stated | Defined RPO/RTO per domain, tested failover |
| Data | OLTP only | Lakehouse + streaming + ML + FinOps |

---

# 9. Proposed ADRs

New decisions to ratify before building the above (each becomes an `architecture/adr/ADR-0XX`):

- Real-time charging architecture (OCS) and balance consistency model.
- Cell-based / multi-region topology and subscriber sharding key.
- Service mesh and zero-trust (mTLS, SPIFFE, OPA policy-as-code).
- Secrets, tokenization, and key management (Vault + HSM/KMS).
- Data platform: lakehouse, CDC strategy, semantic layer, governance.
- ML lifecycle: feature store, model serving, monitoring, and governance.
- Consent and data-lifecycle (DSAR, erasure, retention, residency).
- Supply-chain security (SBOM, signing, provenance) in CI/CD.

---

# 10. Adoption Roadmap (P6+)

Indicative sequencing; each phase exits with its SLOs met and ADRs ratified.

| Phase | Theme | Headline capabilities |
| --- | --- | --- |
| P6 | Channels and corporate | BFFs, corporate/fleet accounts, e-Fatura |
| P7 | Real-time charging | OCS, rating engine, prepaid + top-up, roaming |
| P8 | Zero-trust and compliance | service mesh/mTLS, consent, DSAR/erasure, HSM/KMS |
| P9 | Scale and resilience | cells, multi-region, DR drills, chaos engineering |
| P10 | Data and intelligence | lakehouse, streaming, churn/NBA, fraud, FinOps, AIOps |
| P11 | Growth | campaign engine, MNP at scale, regulatory reporting suite |

> Sequencing is value- and risk-driven: channels/corporate unlock revenue early; charging and
> zero-trust are prerequisites for prepaid and for handling PII at scale; data/intelligence compounds
> once the event and lakehouse foundations exist.

_- End of Document -_
