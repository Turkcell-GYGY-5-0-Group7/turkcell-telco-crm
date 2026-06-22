# Sprint 08 - Order and Payment

## Objective

Build order-service (9004) and payment-service (9008): order capture with synchronous customer/price
validation, order state model, the saga state foundation, and event emission; plus a mock-PSP payment
service with idempotency and timed retry. This sprint assembles the producing/consuming halves that
Sprint 09 stitches into the full onboarding saga.

Covers FR-09, FR-10, FR-11, FR-12 (order) and FR-25, FR-26, FR-27 (payment).

## Included Epics

- Epic 8: Order Orchestration and Payment (order-service, payment-service)

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 8.1 | Order Service - Scaffold and Schema | [8.1-order-service-scaffold-and-schema.md](8.1-order-service-scaffold-and-schema.md) |
| 8.2 | Order Service - Domain and Validation | [8.2-order-service-domain-and-validation.md](8.2-order-service-domain-and-validation.md) |
| 8.3 | Order Service - Application | [8.3-order-service-application.md](8.3-order-service-application.md) |
| 8.4 | Payment Service - Scaffold and Schema | [8.4-payment-service-scaffold-and-schema.md](8.4-payment-service-scaffold-and-schema.md) |
| 8.5 | Payment Service - Domain and Application | [8.5-payment-service-domain-and-application.md](8.5-payment-service-domain-and-application.md) |
| 8.6 | Tests | [8.6-tests.md](8.6-tests.md) |

## Sprint Deliverables

- order-service (9004): order capture with sync customer/price validation and snapshot, status
  machine, saga-state init, create/get/cancel endpoints, and `order.created.v1`/`order.cancelled.v1`.
- payment-service (9008): mock PSP, idempotent charge, order.created consumer, 24/72/168h retry,
  refund, and payment events.
- Integration tests for both.

## Exit Criteria

- An order can be placed (PENDING_PAYMENT) with a validated customer and price snapshot, emitting
  `order.created.v1`; payment-service consumes it and emits `payment.completed.v1`/`payment.failed.v1`.
- Idempotency holds for both order creation and payment charging; failed payments retry on schedule;
  refunds are idempotent.
- FR-09..12 and FR-25..27 pass at the service level (full saga wiring completes in Sprint 09).
</content>
