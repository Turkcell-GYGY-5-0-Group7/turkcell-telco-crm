# CLAUDE.md - campaign-service

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004, ADR-027 Decision Section 2).
- Base package: `com.telco.campaign`.
- Owns the `campaign` PostgreSQL database (`campaign-db`, database-per-service, ADR-006). No other
  service accesses `campaign-db` directly.
- Infrastructure Profile (ADR-006): **transactional, per-customer-consistent - not cache-aside.**
  Contrast explicitly with `product-catalog-service`, which is read-heavy/broadcast reference data
  behind a Redis cache-aside layer (`docs/architecture/service-catalog.md` Section 5).
  campaign-service's redemption-cap counting must be strongly consistent under concurrent order
  attempts (a customer must never redeem past their cap), so every read/write goes straight to
  PostgreSQL inside a transaction - there is no cache layer here, and none should be added without a
  tech-lead ruling. See
  `docs/tasks/sprint-21-campaign-catalog-validation/design-note.md` Section 2 for the full rationale.

## Layout

- `api/` - thin controllers (HTTP -> mediator -> ApiResult). Not populated yet (Feature 21.3).
- `application/` - commands, queries, handlers, DTOs, versioned event payloads. Not populated yet
  (Features 21.2-21.4).
- `domain/model/` - `Campaign`, `CampaignRedemption` aggregates plus `CampaignStatus`,
  `DiscountType`, `RedemptionStatus` enums. Bare JPA entities only as of Feature 21.1 - no domain
  behavior methods yet (state transitions, eligibility, redemption-cap enforcement land in 21.2).
- `infrastructure/persistence/` - `CampaignRepository`, `CampaignRedemptionRepository`.
- `infrastructure/config/` - `CampaignSecurityConfig` (JWT filter chain wiring, ADR-011).

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult` via `ApiResponseFactory`. No business logic in controllers (ADR-004, ADR-008).
- Commands and queries are immutable records implementing `Command<R>` / `Query<R>`; one handler
  each (`@Component`), resolved by generics. Queries do not change state.
- `campaigns.applicable_tariff_codes` is a normalized child table (`campaign_tariff_codes`) mapped
  via `@ElementCollection`, storing opaque product-catalog tariff **codes** only - never a copy of
  tariff pricing data (ADR-027 Decision Section 3). Do not join or replicate `product-catalog-db`.
- The order-service -> campaign-service validate call (Feature 21.3) is synchronous
  (REST/OpenFeign + Resilience4j circuit breaker, ADR-005) and **fail-open**: if campaign-service is
  unreachable, order-service proceeds at the undiscounted price. A campaign outage must never block
  order creation.
- Redemption lifecycle (Feature 21.4): `RESERVED` on consuming `order.created.v1`, `CONFIRMED` on
  consuming `payment.completed.v1` (not `order.confirmed.v1` - deferred/unproduced, ADR-027 Section 4
  amendment), `RELEASED` on consuming `order.cancelled.v1`. A `@Scheduled` reservation-expiry reaper
  coordinated via `starter-lock` is required per the same ADR amendment - not yet built.
  `campaign_redemptions.order_id` is nullable pending that wiring.
- When adding persistence writes: publish domain events through `starter-outbox`
  (`OutboxService`), never directly to Kafka. The `aggregateType` passed to
  `outboxService.publish(...)` is the Debezium ROUTING key and MUST be the lowercase domain
  (`campaign`) - see `platform/PLATFORM-SPEC.md` Section 5.
- Event types follow `domain.event.v1` (ADR-009, ADR-019).
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015). No gateway route is registered for
  campaign-service (internal service-to-service call only, ADR-005/ADR-011); a route would only be
  needed for a future admin-facing campaign management UI (explicitly deferred, Feature 21.1.3).
- Schema changes ship as Flyway migrations under `db/migration`; platform tables (outbox/inbox) come
  from `classpath:db/migration/platform`.
- No emojis anywhere.

## When generating code here

- Put commands/queries/handlers/DTOs under `application/`, controllers under `api/`, JPA entities
  under `domain/model/`, repositories under `infrastructure/persistence/`.
- Add focused unit tests for handlers (no Spring needed) plus `@DataJpaTest`/Testcontainers repository
  and Flyway-migration tests, following `product-catalog-service`'s
  `CatalogRepositoryTest`/`CatalogSchemaMigrationTest` pattern.
