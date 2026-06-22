# Sprint 09 - Subscription and Onboarding Saga

## Objective

Build subscription-service (9005) with its lifecycle state machine and MSISDN allocation, then wire
the end-to-end new-line onboarding saga (order -> payment -> subscription -> fulfillment + welcome
SMS) including compensation on failure. Completing this sprint delivers acceptance criterion AC-01.

Covers FR-13, FR-14, FR-15 (FR-16 MNP is post-MVP, scaffolded only) and the saga orchestration of
FR-10/FR-12.

## Included Epics

- Epic 9: Subscription Lifecycle and Onboarding Saga (subscription-service + cross-service saga)

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 9.1 | Subscription Service - Scaffold and Schema | [9.1-subscription-service-scaffold-and-schema.md](9.1-subscription-service-scaffold-and-schema.md) |
| 9.2 | Subscription Service - Domain | [9.2-subscription-service-domain.md](9.2-subscription-service-domain.md) |
| 9.3 | Subscription Service - Application and Lifecycle Endpoints | [9.3-subscription-service-application-and-lifecycle-endpoints.md](9.3-subscription-service-application-and-lifecycle-endpoints.md) |
| 9.4 | Onboarding Saga Wiring | [9.4-onboarding-saga-wiring.md](9.4-onboarding-saga-wiring.md) |
| 9.5 | AC-01 End-to-End | [9.5-ac-01-end-to-end.md](9.5-ac-01-end-to-end.md) |

## Sprint Deliverables

- subscription-service (9005): lifecycle state machine, atomic MSISDN allocation/release, activation
  and lifecycle endpoints, MNP scaffold (deferred), and subscription events.
- Fully wired onboarding saga across order/payment/subscription with compensation.
- AC-01 end-to-end integration test (happy path and compensation).

## Exit Criteria

- AC-01 passes end to end: a registered, KYC-approved customer orders a postpaid tariff, pays via
  mock PSP, gets an automatically activated subscription with an allocated MSISDN, a welcome signal,
  and a FULFILLED order.
- A forced activation failure compensates (refund + order CANCELLED) with no dangling MSISDN.
- FR-13, FR-14, FR-15 pass; FR-10 and FR-12 saga behavior validated; FR-16 scaffolded as post-MVP.
</content>
