# Sprint 21 - Campaign / Catalog Validation (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. It is documented now (design pass + Proposed ADR) and built later.
> Feature subtask files will be authored when the sprint is scheduled.

## Objective

Deliver dynamic-pricing and campaign-limit validation at order/catalog time: a new `campaign-service`
that order-service calls synchronously at order-creation to price a discount and enforce
per-customer/total redemption caps and validity windows, without requiring the segment/data-platform
capabilities TELCO-CRM-ADVANCED.md Section 2.4 assumes. Built per ADR-027 (new service, CQRS +
Mediator, ADR-006 database-per-service) and reusing order-service's existing synchronous
catalog-price-snapshot pattern (ADR-005).

## Included Epics

- Epic 21: Campaign and Catalog Validation

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 21.1 | campaign-service scaffold and schema (ADR-017 template, `campaign-db`, Campaign/CampaignRedemption) | TODO | [21.1-campaign-service-scaffold-and-schema.md](21.1-campaign-service-scaffold-and-schema.md) |
| 21.2 | Campaign domain: eligibility rules, redemption limits, validity windows | TODO | [21.2-campaign-domain-eligibility-and-limits.md](21.2-campaign-domain-eligibility-and-limits.md) |
| 21.3 | Campaign validation API + order-service integration (sync call, fail-open circuit breaker) | TODO | [21.3-campaign-validation-api-and-order-integration.md](21.3-campaign-validation-api-and-order-integration.md) |
| 21.4 | Campaign eventing: outbox (campaign lifecycle) + inbox (order confirm/cancel, tariff events) | TODO | [21.4-campaign-eventing-outbox-inbox.md](21.4-campaign-eventing-outbox-inbox.md) |
| 21.5 | Tests (unit/integration/contract) | TODO | [21.5-tests.md](21.5-tests.md) |

## Sprint Deliverables

- `campaign-service` (new, port 9011 proposed) with its own `campaign-db` (PostgreSQL).
- order-service calls `POST /api/v1/campaigns/validate` synchronously at order-creation time and
  snapshots the discounted price into the OrderItem.
- Redemption caps (`totalRedemptionCap`, `perCustomerRedemptionCap`) enforced against
  `CampaignRedemption`, committed on order confirmation, released on order cancellation.

## Exit Criteria

- An order placed against an ACTIVE campaign within its validity window and under its redemption caps
  is priced with the campaign discount; an ineligible order is priced at the full catalog price with a
  clear reason surfaced.
- campaign-service unavailability does not block order creation (fail-open circuit breaker verified).
- No shared database access between campaign-service and product-catalog-service/order-service
  (ADR-006 verified).

## References

- [ADR-027 Campaign and Catalog Validation](../../../architecture/adr/ADR-027-campaign-and-catalog-validation.md)
- [design-note.md](design-note.md)
- [service-catalog.md](../../architecture/service-catalog.md)
- [event-catalog.md](../../architecture/event-catalog.md)
