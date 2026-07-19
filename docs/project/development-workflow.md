# Development Workflow

## Coding pattern: CQRS + Mediator

Most domain services are `CQRS + Mediator` mode ([ADR-004](../adr/ADR-004-architecture-style.md)).
The shape is consistent everywhere:

```text
Controller (thin)
   -> Mediator.send(command) / Mediator.query(query)
        -> pipeline behaviors (validation, authz, logging, transaction, performance, inbox)
             -> Handler (one class, one command or query, stateless)
                  -> domain model (framework-free) + repository
                  -> OutboxService.publish(...) if a domain event must fire
```

Controllers never contain business logic ([ADR-008](../adr/ADR-008-cqrs-and-mediator-strategy.md)):
they parse the request, dispatch through the `Mediator`, and map the result to `ApiResult`.
Domain logic never depends on Spring, Kafka, or JPA directly - it depends on ports the
infrastructure layer implements.

## Starting a new service

1. Copy `microservices/service-template` (minimal) or `microservices/reference-service` (adds
   JPA/Flyway/outbox) - see [ADR-017](../adr/ADR-017-service-template-standard.md).
2. Rename the artifactId/package, set the port (check the [Service Catalog](../architecture/service-catalog.md)
   for the next free one), and register the module in `microservices/pom.xml`.
3. Declare the Architecture Mode and Infrastructure Profile in the new service's own `README.md`.
4. Add only the starters the service actually needs (see
   [Platform & Reuse-Before-Build](platform-and-reuse.md)).
5. Add a `microservices/configs/<service-name>/` directory with `application.yml` + per-profile
   overrides, mirroring an existing service.
6. Write the Testcontainers integration tests before calling it done.

## Database migrations (ADR-016)

Flyway is the single, mandatory migration tool. Each service owns and versions its own migrations
independently - there is no shared migration file and no environment-specific SQL. Migrations are
immutable once merged: a mistake is fixed with a new forward migration, never an edit to an
already-merged one. The same migration set runs identically across local, test, staging, and
production; CI validates this and checks for schema drift.

## Testing strategy (ADR-013)

A four-layer pyramid:

| Layer | Runs against | Speed | Gate |
| --- | --- | --- | --- |
| Unit | No Spring context, no DB | <50ms/test | Every commit |
| Integration | Testcontainers (Postgres, Kafka, Redis) | seconds | Required to merge |
| Contract | REST/gRPC/Avro schema compatibility | seconds | Required to merge |
| End-to-end | Full stack, critical flows only (billing, order, payment) | minutes | Release pipelines |

Outbox publishing and inbox idempotency are explicitly required test targets, not incidental
coverage - a handler that publishes an event needs a test proving the outbox row and the domain
write commit together, and a consumer needs a test proving redelivery of the same message collapses
to one effect. CI enforces a 70% JaCoCo line-coverage gate per module
([`ci.yml`](deployment-and-operations.md#cicd-github-actions)).

## Conventions worth internalizing early

- No emojis anywhere: code, comments, commit messages, or documentation.
- Never hardcode a dependency version in a service `pom.xml` - everything comes from
  `platform-bom`.
- Never call the Kafka client API directly - always `OutboxService`/inbox.
- Never depend on `platform-core` directly - always a starter.
- Every external endpoint returns `ApiResult<T>` under `/api/v1`.

## This repository is built agent-first

The project's `CLAUDE.md` defines a hierarchy of specialized subagents that actually build and
review most of this codebase's changes. Understanding this hierarchy explains a lot about *why*
the repo is organized the way it is (heavy ADR discipline, strict layering, a sprint backlog with
per-feature build-and-verification records):

| Agent | Authority | Used for |
| --- | --- | --- |
| `tech-lead` | Root, final | Cross-service or platform-core conflicts and decisions |
| `product-owner` | Planning | Roadmap, sprint/task breakdown, status updates |
| `architecture` | Semi (per-service) | Design validation, architecture-mode assignment |
| `platform-engineer` | Semi (platform/) | Platform-core internals, starters, autoconfigure |
| `microservice-generator` | Generation | Scaffolding a new service from the ADR-017 template |
| `domain-engineer` | Semi (service logic) | Business logic, handlers, domain events |
| `event-integration` | Semi (events) | Avro contracts, Schema Registry, outbox/inbox wiring |
| `security` | Semi (security) | JWT, RBAC, PII encryption/masking, audit, rate limiting |
| `observability` | Semi (telemetry) | Tracing, metrics, structured logging, dashboards |
| `devops` | Semi (build/deploy) | Maven/BOM, CI/CD, Docker, Kubernetes, local infra stack |
| `qa` | Quality gate | Unit/integration/contract/acceptance tests, coverage |
| `code-review` | Enforcing, read-only | ADR-compliance review before commit/PR |

If a change is uncertain in scope or crosses a service boundary, the rule is: consult
`architecture`, validate with `tech-lead`, never assume silently. The same rule applies whether a
human or an agent is making the change.

## Delivery tracking

Every feature lives in `docs/tasks/sprint-NN-*/`, each with its own README and per-feature files.
[`docs/tasks/STATUS.md`](../tasks/STATUS.md) is the single live cross-sprint status dashboard -
update the sprint README and STATUS.md together whenever a feature's state changes.
[`docs/tasks/lessons.md`](../tasks/lessons.md) captures patterns from past corrections; read it
before starting new work, not just when something goes wrong.

## Before opening a pull request

Read [Contributing](contributing.md) - the PR template requires an architecture-compliance
checklist, and `code-review` (or a human doing the same review) checks ADR compliance before
anything merges.
