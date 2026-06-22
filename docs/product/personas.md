# Personas

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Actor and Persona Definitions |
| Version | 1.0 |
| Parent | [BRD.md](./BRD.md) |
| Last updated | 2026-06-19 |

This document expands the actors in [BRD.md](./BRD.md) Section 4 into working personas. Each
persona lists goals, pain points, and the primary functional requirements they exercise.

---

## P1. Subscriber (End-User)

- Profile: An individual mobile customer of TelcoX using web/mobile self-service.
- Goals: Get a line activated quickly, understand remaining quota, pay bills, get help fast.
- Pain points: Slow activation, surprise charges, no visibility into usage.
- Key journeys: Onboarding (AC-01), quota check, invoice viewing and payment, ticket creation.
- Primary requirements: FR-01..04, FR-09, FR-13, FR-18, FR-25, FR-31.

## P2. Call Center Agent

- Profile: Customer support representative resolving inbound contacts.
- Goals: Resolve tickets within SLA, see accurate subscriber state, make manual changes.
- Pain points: Fragmented customer view, slow lookups, unclear SLA timers.
- Key journeys: Ticket triage and resolution, subscriber inspection, manual plan change.
- Primary requirements: FR-31..33, FR-03, FR-14.

## P3. Field Dealer

- Profile: Retail dealer activating subscribers in store.
- Goals: Activate new subscribers and sell SIMs with minimal friction; capture KYC correctly.
- Pain points: Re-keying customer data, KYC rejections, SIM/MSISDN allocation delays.
- Key journeys: New subscriber activation, KYC data entry, SIM sale.
- Primary requirements: FR-01, FR-02, FR-09, FR-13.

## P4. Marketing Manager (post-MVP focus)

- Profile: Owns tariff positioning and campaigns.
- Goals: Launch tariffs/segments quickly; analyze adoption.
- Pain points: Slow catalog changes, no segmentation tooling (campaign engine is post-MVP).
- Key journeys: Tariff definition input, segmentation (post-MVP).
- Primary requirements: FR-05..08 (campaign/segmentation deferred per BRD 7.2).

## P5. System Administrator

- Profile: Platform administrator managing catalog and access.
- Goals: Maintain accurate product catalog; manage users, roles, and permissions safely.
- Pain points: Risky bulk changes, lack of audit trail.
- Key journeys: Catalog CRUD, user/role administration, KYC approval.
- Primary requirements: FR-05..08, FR-IAM-04, FR-02.

## P6. Billing Operator

- Profile: Finance/operations user overseeing invoicing.
- Goals: Ensure bill-run completes correctly and on time; handle invoice corrections.
- Pain points: Long bill-run windows, manual reconciliation, invoice disputes.
- Key journeys: Trigger/monitor bill-run, invoice cancellation, payment reconciliation.
- Primary requirements: FR-21..24.

## P7. Internal Service (System Actor)

- Profile: Service-to-service interactions, scheduled jobs, CDR mediation.
- Goals: Reliable event publish/consume, idempotent processing, replayable streams.
- Pain points: Duplicate events, ordering issues, schema drift.
- Key journeys: Event publish via outbox, idempotent consume via inbox, scheduled bill-run, CDR ingestion.
- Primary requirements: ARC-05, ARC-06, FR-17, FR-21.

---

Document end.
