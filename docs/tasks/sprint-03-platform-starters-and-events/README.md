# Sprint 03 - Platform Starters and Event Contracts

## Objective

Expose `platform-core` as Spring Boot starters (the only thing services depend on, ADR-018), define
the Avro event contracts and shared envelope (ADR-019), and ship the service template plus a
reference service that proves the whole stack wires together. After this sprint a new domain service
is a copy-and-fill exercise.

## Included Epics

- Epic 3: Platform Starters, Event Contracts, and Service Template

## Cross-cutting constraints

- Spring lives ONLY in `platform-autoconfigure` and `platform-starters/*`. Each starter ships
  `@AutoConfiguration` classes, conditional beans, typed `@ConfigurationProperties`, and an
  `AutoConfiguration.imports` file. Property prefix root `telco.platform`. No business logic.

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 3.1 | Shared Autoconfigure | [3.1-shared-autoconfigure.md](3.1-shared-autoconfigure.md) |
| 3.2 | Starters | [3.2-starters.md](3.2-starters.md) |
| 3.3 | Event Contracts (Avro) | [3.3-event-contracts-avro.md](3.3-event-contracts-avro.md) |
| 3.4 | Service Template and Reference Service | [3.4-service-template-and-reference-service.md](3.4-service-template-and-reference-service.md) |

## Sprint Deliverables

- Six Spring Boot starters (api, mediator, security, outbox, inbox, observability) plus shared
  autoconfigure and structured-JSON/PII-masking logging.
- Avro event contracts with envelope, MVP schemas, and Schema Registry compatibility gating.
- Service template (ADR-017) and a working reference-service with Testcontainers integration tests.

## Exit Criteria

- A new service can be created from the template depending only on starters and immediately get
  ApiResult, mediator pipeline, outbox/inbox, security, and correlation/tracing for free.
- reference-service integration tests pass against Testcontainers Postgres + Kafka.
- An incompatible Avro change fails the build; structured logs are JSON with PII masked.
</content>
