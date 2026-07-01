# Sprint 10 - Usage Metering

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 7/7 | 2026-07-01 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build usage-service (9006): consume CDR events from Kafka, decrement quota balances, expose
near-real-time remaining quota, emit 80%/100% threshold events, and aggregate overage for billing.
Provide a CDR simulator to drive the flow. Delivers acceptance criterion AC-03.

Covers FR-17, FR-18, FR-19, FR-20.

## Included Epics

- Epic 10: Usage and Quota (usage-service + CDR simulator)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 10.1 | Scaffold and Schema | DONE | [10.1-scaffold-and-schema.md](10.1-scaffold-and-schema.md) |
| 10.2 | Quota Provisioning | DONE | [10.2-quota-provisioning.md](10.2-quota-provisioning.md) |
| 10.3 | CDR Ingestion and Metering | DONE | [10.3-cdr-ingestion-and-metering.md](10.3-cdr-ingestion-and-metering.md) |
| 10.4 | Thresholds and Overage | DONE | [10.4-thresholds-and-overage.md](10.4-thresholds-and-overage.md) |
| 10.5 | Read API | DONE | [10.5-read-api.md](10.5-read-api.md) |
| 10.6 | CDR Simulator | DONE | [10.6-cdr-simulator.md](10.6-cdr-simulator.md) |
| 10.7 | Tests | DONE | [10.7-tests.md](10.7-tests.md) |

## Sprint Deliverables

- usage-service (9006): quota provisioning on activation, idempotent CDR metering, 80%/100% threshold
  events, overage capture and aggregation, quota/history read APIs, and a CDR simulator.
- AC-03 integration test.

## Exit Criteria

- AC-03 passes: CDR events decrement quota, an 80% warning event and a 100% exceeded event fire once
  each, and post-exhaustion usage is forwarded to billing as overage via `usage.aggregated.v1`.
- Metering is idempotent (by cdrRef) and concurrency-safe; quota reads are near-real-time.
- FR-17, FR-18, FR-19, FR-20 pass.
</content>
