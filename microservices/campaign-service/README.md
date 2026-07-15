# campaign-service

| Field | Value |
| --- | --- |
| Port | 9011 |
| Architecture Mode (ADR-004) | CQRS + Mediator |
| Infrastructure Profile (ADR-006) | PostgreSQL (transactional, per-customer-consistent - not cache-aside) |
| Owning sprint | [Sprint 21](../../docs/tasks/sprint-21-campaign-catalog-validation/README.md) |
| Status | Skeleton (no business code) |

**Architecture Mode: CQRS + MEDIATOR** (ADR-004, ADR-027 Decision Section 2)

Campaign and catalog-limits validation: owns the `Campaign` and `CampaignRedemption` aggregates in
its own `campaign-db` (PostgreSQL, database-per-service, ADR-006). Called synchronously by
order-service at order-creation time to validate campaign eligibility and compute a discount
(`docs/api-contracts/campaign-service.md`); redemption caps are enforced by counting `CONFIRMED` and
still-live `RESERVED` rows.

## Infrastructure profile - contrast with product-catalog-service

product-catalog-service is read-heavy, broadcast reference data with a Redis cache-aside profile
(`docs/architecture/service-catalog.md` Section 5). campaign-service is the opposite shape: a
time-boxed campaign lifecycle plus per-customer redemption state that must stay strongly consistent
under concurrent order attempts (a customer must never redeem past their cap). That is a
write-heavy, per-customer-transactional workload, not a cacheable one - so campaign-service has
**no cache layer**; every read goes straight to PostgreSQL inside a transaction. See
[design-note.md Section 2](../../docs/tasks/sprint-21-campaign-catalog-validation/design-note.md#2-service-boundary-decision)
for the full rationale for splitting this out of product-catalog-service.

## Scope of this build (Sprint 21 Feature 21.1)

This is a schema-and-skeleton scaffold only:

- `campaign-db` Flyway migration (`campaigns`, `campaign_tariff_codes`, `campaign_redemptions`) plus
  platform outbox/inbox tables.
- Bare `Campaign` / `CampaignRedemption` JPA entities (fields and column mappings only) and Spring
  Data repositories.
- No domain behavior (eligibility, redemption-cap enforcement, validity windows) - see
  [21.2](../../docs/tasks/sprint-21-campaign-catalog-validation/21.2-campaign-domain-eligibility-and-limits.md).
- No API (`POST /api/v1/campaigns/validate`) - see
  [21.3](../../docs/tasks/sprint-21-campaign-catalog-validation/21.3-campaign-validation-api-and-order-integration.md).
- No eventing/outbox-inbox wiring or reservation-expiry reaper - see
  [21.4](../../docs/tasks/sprint-21-campaign-catalog-validation/21.4-campaign-eventing-outbox-inbox.md).

## Platform wiring

- Platform starters only (ADR-018): `starter-api`, `starter-security`, `starter-observability`,
  `starter-mediator`, `starter-outbox`, `starter-inbox` (the last two inherited from
  `domain-services-parent` plus `starter-mediator`/`starter-inbox` declared directly - see `pom.xml`).
- Runtime config is centralized in config-server; this module keeps only the `spring.config.import`
  bootstrap (`microservices/configs/campaign-service/`). Reuse platform capabilities first
  ([catalog](../../docs/architecture/platform-capabilities.md)).

## Run (dev)

```bash
SPRING_PROFILES_ACTIVE=dev mvn -pl campaign-service spring-boot:run
curl localhost:9011/actuator/health
```

Contract: [campaign-service](../../docs/api-contracts/campaign-service.md).
