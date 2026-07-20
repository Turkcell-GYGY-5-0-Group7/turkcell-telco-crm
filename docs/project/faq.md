# FAQ

Questions a new engineer usually hits in the first week.

## Why can't a service call the Kafka client API directly?

Because a direct write and a Kafka publish can't commit atomically - if the database write
succeeds and the Kafka publish fails (or vice versa), the system is left inconsistent with no
clean way to detect it. The **transactional outbox** (`OutboxService.publish(...)`) writes the
event to a table in the *same* database transaction as the domain change, so they succeed or fail
together; Debezium then relays that row to Kafka asynchronously. On the consuming side, the
**inbox** guarantees a redelivered message is only ever acted on once. See
[Events & Messaging](events-and-messaging.md) and
[ADR-009](../adr/ADR-009-event-driven-architecture.md).

## Why can't a service depend on `platform-core` directly?

`platform-core` modules are framework-agnostic building blocks with no Spring dependency at all -
they are not meant to be wired up by hand in every service. The `platform-starters/*` modules
wrap them with Spring auto-configuration, so a service gets working, tested integration for free
by adding one starter dependency instead of hand-assembling `platform-core` pieces. The one
narrow exception is `platform-event-contracts` (schema-only, no runtime behavior). See
[ADR-018](../adr/ADR-018-platform-starter-dependency-model.md).

## How do I add a new event?

1. Define the Avro schema under `platform/platform-event-contracts/src/main/avro/`, following the
   `domain.event.v1` naming convention.
2. Add the payload record and register the subject in that module's pom.
3. Publish it from a command handler via `OutboxService.publish(aggregateType, aggregateId,
   eventType, payload)` - never a direct Kafka call.
4. Add a Debezium outbox connector entry under `infra/docker/kafka-connect/connectors/` if the
   producing service doesn't already have one.
5. Add a `*EventSchemaCompatTest` in the producing service that checks the real payload class
   against the registered schema - this becomes a standing CI gate.
6. Add a row to the [Event Catalog](../architecture/event-catalog.md)'s registry table.

See [ADR-019](../adr/ADR-019-event-contract-and-schema-governance.md) for the full governance
rule.

## How do I add a new service?

Start from [Development Workflow](development-workflow.md#starting-a-new-service). In short: copy
`service-template` or `reference-service`, declare its architecture mode and infrastructure
profile, add only the starters it needs, register it in the Maven reactor and in
`microservices/configs/`, and add it to the [Service Catalog](../architecture/service-catalog.md).

## Why does my service need to declare an "architecture mode"?

So a reader (human or agent) never has to guess how much structure to expect. A service that says
"Simple Service Layer" is CRUD and should stay that way; one that says "CQRS + Mediator" or
"Domain Orchestration" is expected to actually use the mediator pipeline and, for orchestration,
coordinate other services through events rather than direct calls. Declaring the wrong mode - or
mixing modes without approval - is treated as an architecture violation. See
[ADR-004](../adr/ADR-004-architecture-style.md).

## The local stack won't start / runs out of memory. What do I do?

Bring up only the profile you actually need instead of the full stack - `make dev` (core + auth)
is usually enough for feature work on a single service. If you need more services running
simultaneously, use the curated `make infra-sprint16-e2e` subset rather than `make full`. See
[Getting Started](getting-started.md#bringing-up-more-of-the-stack) for the full profile table and
the memory-budget note.

## A service won't start after `make infra-destroy` and re-up. Why?

`infra-destroy` deletes data volumes, so Postgres, Kafka topics, Keycloak's realm data, and MinIO
buckets are all gone. Debezium connectors also need to be re-registered after a fresh start
(`make infra-connectors`) since they depend on PostgreSQL publications that no longer exist post-
destroy. If only one service is unhealthy, check `make infra-logs S=<container>` first before
assuming a wider problem.

## Where do I find the actual current delivery status, not just the roadmap?

[`docs/tasks/STATUS.md`](../tasks/STATUS.md) - it is the live dashboard, updated every time a
feature's state changes, and it is intentionally honest about what was live-verified versus only
authored/compiled. The [Roadmap & Status](roadmap-and-status.md) page here is a snapshot that will
go stale; STATUS.md will not.

## What does "gateway-behind-trust" mean?

The API Gateway is the only component that validates the incoming JWT. It strips any
client-supplied `X-User-Id`/`X-User-Roles` headers and re-injects them from the verified token
before forwarding the request. Downstream services trust those headers because only the gateway
can set them from a valid JWT - they don't re-validate the JWT themselves by default (though
`starter-security` can do so for defense-in-depth). See
[Security & Identity](security-and-identity.md).

## Something here contradicts an ADR. Which one wins?

The ADR. `CLAUDE.md`'s rule is explicit: ADRs win over prose, a tech-lead decision wins over
everything, and the `docs/tasks` backlog is authoritative for delivery status. This documentation
site follows the same rule - if a curated page here and an ADR ever disagree, treat that as a bug
in this site, not in the ADR.
