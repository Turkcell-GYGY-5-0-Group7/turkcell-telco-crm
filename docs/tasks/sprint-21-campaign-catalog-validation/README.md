# Sprint 21 - Campaign / Catalog Validation (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 5/5 | 2026-07-15 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. It was documented with a Proposed ADR and is now cleared to build:
> ADR-027 was ratified (Accepted) by tech-lead on 2026-07-13, with a Section 4 amendment resolving the
> redemption-lifecycle open items below (see ADR-027 Section 4 ratification note and
> `docs/tasks/STATUS.md` 2026-07-13 entry for the full reasoning). Build work started 2026-07-13 with
> Feature 21.1 (scaffold and schema only - no domain behavior, API, or eventing yet).

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
| 21.1 | campaign-service scaffold and schema (ADR-017 template, `campaign-db`, Campaign/CampaignRedemption) | DONE | [21.1-campaign-service-scaffold-and-schema.md](21.1-campaign-service-scaffold-and-schema.md) |
| 21.2 | Campaign domain: eligibility rules, redemption limits, validity windows | DONE | [21.2-campaign-domain-eligibility-and-limits.md](21.2-campaign-domain-eligibility-and-limits.md) |
| 21.3 | Campaign validation API + order-service integration (sync call, fail-open circuit breaker) | DONE | [21.3-campaign-validation-api-and-order-integration.md](21.3-campaign-validation-api-and-order-integration.md) |
| 21.4 | Campaign eventing: outbox (campaign lifecycle) + inbox (order confirm/cancel, tariff events) | DONE | [21.4-campaign-eventing-outbox-inbox.md](21.4-campaign-eventing-outbox-inbox.md) |
| 21.5 | Tests (unit/integration/contract) | DONE | [21.5-tests.md](21.5-tests.md) |

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

## Follow-up (2026-07-13) - Feature 21.3 live-verification gap closed

Feature 21.3's own file noted one deferred item: the order-service side of the live end-to-end proof
(21.3.3's acceptance criteria) could not be completed in the session that built it, because the reused
local dev `order_db` had already applied the platform outbox/inbox migrations (900/901) before the new
`V7__order_items_campaign.sql` was added, and an unauthorized attempt to drop/recreate the database was
correctly blocked. This follow-up session confirmed `order_db` was genuinely empty first (no relations,
no `flyway_schema_history`), reseeded it with explicit user authorization, and completed both proofs:

- A real campaign (`SUMMER25E2E`, `PERCENTAGE` 25%, applicable to a real ACTIVE tariff `CAMP21E2E`) was
  created and activated, and a real order placed through order-service priced the `OrderItem` at the
  discounted `unitPrice` (75.00 from a 100.00 `monthlyFee`) with the correct `campaignId` recorded -
  verified both via the HTTP response and directly against `order_items` in Postgres.
- campaign-service was then made unreachable (process killed, port confirmed connection-refused), and a
  second real order for the same tariff/customer was still created successfully at the full 100.00
  undiscounted price with `campaignId` NULL, proving the fail-open guarantee end-to-end (not just at the
  `CampaignServiceClient` unit-test level as before). order-service's own log shows the fail-open path
  firing (`ResourceAccessException`/connection-refused caught inside `CampaignServiceClient`, never
  propagated).

No open verification gaps remain on Feature 21.3. Detail:
`docs/tasks/STATUS.md` (2026-07-13 top entry), `docs/tasks/lessons.md` (2026-07-13 "out of order"
entry, resolution appended).

## Follow-up (2026-07-15) - Feature 21.5 test suite closes the sprint, all exit criteria test-proven

Feature 21.5.1 was already substantially complete as a byproduct of building 21.2-21.4 (domain/handler
unit tests, `CampaignServiceClientTest`'s fail-open unit proof). This closing session added the
remaining pieces and cross-checked every 21.5 subtask's acceptance criteria against the exit criteria
above:

- `CampaignServiceIntegrationTest` (new, Testcontainers Postgres): full create -> activate -> validate
  (eligible) -> simulate `order.created.v1` (reserve) -> simulate `payment.completed.v1` (confirm) ->
  `perCustomerRedemptionCap`-exceeded-on-next-attempt, end to end through the real admin/`/internal`
  HTTP surface and real Postgres-backed repositories. Also proves idempotent redelivery of
  `ConfirmRedemptionCommand` through the real platform `InboxBehavior`/inbox table (duplicate
  `payment.completed.v1` messageId -> single CONFIRMED transition), the strongest form of that proof in
  the feature.
- `CampaignApiContractTest` (new, reflection-only, mirrors `TariffApiContractTest` - no Spring context
  needed): guards `CampaignResponse`/`CampaignValidationResponse`'s documented field sets and, crucially,
  that `POST /internal/campaigns/validate` stays mounted under `/internal` (tokenless) and not
  `/api/v1/campaigns/validate`, per ADR-027's second ratification addendum.
- `CampaignSchemaMigrationTest` (already existed) extended with a direct ADR-006 assertion: campaign-db's
  migrated schema must never contain another service's tables (`tariffs`, `orders`, `order_items`, etc.)
  - the automated, executable proof of the sprint's third exit criterion, complementing the database-role
    grants already enforced in `infra/docker/postgres/initdb/01-create-databases.sql`.
- The five 21.4 consumer unit tests (`RedemptionCommitEventConsumerTest`,
  `OrderCancelledEventConsumerTest`, `OrderCreatedRedemptionReservationConsumerTest`,
  `TariffCreatedEventConsumerTest`, `TariffPriceChangedEventConsumerTest`) each gained a redelivery test
  proving a duplicate Kafka messageId dispatches an identical (idempotency-key-equal) command every time
  - what the platform `InboxBehavior` (covered by `CampaignServiceIntegrationTest` above, and by
    platform-core's own `InboxBehaviorTest`/`InboxTransactionAtomicityTest`) needs to collapse a
    redelivery to a single effect.
- order-service gained two new integration tests proving the sprint's fail-open/discounted-pricing exit
  criteria end to end, one level below the existing HTTP/mediator/Postgres integration coverage but above
  the client-unit-test level: `CampaignDiscountedOrderIntegrationTest` (real `CampaignServiceClient` bean
  talking over real HTTP to a loopback stub campaign-service) proves a discounted `OrderItem.unitPrice`
  lands in both Postgres and the outbox `order.created.v1` payload; `CampaignServiceFailOpenIntegrationTest`
  proves the same end-to-end path still succeeds at the full undiscounted price when campaign-service is
  unreachable AND when its circuit breaker is forced OPEN. Neither test modifies any pre-existing
  order-service test file (diff-reviewed: zero changes), so the "no regression" acceptance criterion holds
  trivially.
- Verification: `mvn -f microservices/pom.xml -pl campaign-service,order-service -am test
  -Dschema.registry.skip=true` (JAVA_HOME=21) - every non-Testcontainers test class passes live,
  including all of the above. The Testcontainers-backed classes (`CampaignServiceIntegrationTest`,
  `CampaignRepositoryTest`, `CampaignSchemaMigrationTest`, `CampaignEligibilityServiceConcurrencyIT`,
  `OrderServiceIntegrationTest`, `CampaignDiscountedOrderIntegrationTest`,
  `CampaignServiceFailOpenIntegrationTest`, and the rest of order-service's existing Testcontainers
  suite) fail identically with `IllegalStateException: Could not find a valid Docker environment` -
  confirmed as the same pre-existing, repo-wide Testcontainers/Docker-API-version incompatibility
  documented in `docs/tasks/lessons.md` (2026-07-12 entries), reproduced here on every prior Testcontainers
  test in both modules (not just the new ones), so this is not a regression; verified by code review
  instead, mirroring every prior Sprint 21 feature's verification approach.

All three exit criteria above now have at least one direct, executable test: discounted-vs-undiscounted
pricing (`CampaignDiscountedOrderIntegrationTest`, `CampaignServiceFailOpenIntegrationTest`,
`CreateOrderCommandHandlerTest`), fail-open (`CampaignServiceClientTest`,
`CampaignServiceFailOpenIntegrationTest`), and ADR-006 database isolation
(`CampaignSchemaMigrationTest`). Sprint 21 is now 5/5, DONE. Detail: `docs/tasks/STATUS.md` (2026-07-15
top entry).

## References

- [ADR-027 Campaign and Catalog Validation](../../../architecture/adr/ADR-027-campaign-and-catalog-validation.md)
- [design-note.md](design-note.md)
- [service-catalog.md](../../architecture/service-catalog.md)
- [event-catalog.md](../../architecture/event-catalog.md)
