# Sprint 07 - Product Catalog Domain

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-06-22 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Build product-catalog-service (9003): hierarchical management of tariffs, addons, and VAS products
with validity dates and target segments, postpaid/prepaid/hybrid classification, versioned tariff
changes that preserve existing subscribers' tariff, and a Redis cache-aside read path (this is a
read-heavy service). Provides the priced products that order-service (Sprint 08) snapshots.

Covers FR-05, FR-06, FR-07, FR-08.

## Included Epics

- Epic 7: Product and Tariff Catalog (product-catalog-service)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 7.1 | Scaffold and Schema | TODO | [7.1-scaffold-and-schema.md](7.1-scaffold-and-schema.md) |
| 7.2 | Domain and Persistence | TODO | [7.2-domain-and-persistence.md](7.2-domain-and-persistence.md) |
| 7.3 | Caching | TODO | [7.3-caching.md](7.3-caching.md) |
| 7.4 | Application (Commands, Queries, Endpoints) | TODO | [7.4-application-commands-queries-endpoints.md](7.4-application-commands-queries-endpoints.md) |
| 7.5 | Tests | TODO | [7.5-tests.md](7.5-tests.md) |

## Sprint Deliverables

- product-catalog-service (9003): tariff/addon CRUD, classification, validity windows, versioned
  price changes preserving existing subscribers, Redis cache-aside reads, price-snapshot endpoint,
  catalog events, and integration tests.

## Exit Criteria

- Admins can author tariffs/addons; customers and order-service can browse and snapshot prices.
- A tariff price change creates a new version while preserving prior versions; reads are cache-served
  and invalidated correctly.
- FR-05, FR-06, FR-07, FR-08 pass; `tariff.created.v1` and `tariff.price-changed.v1` are published.
</content>
