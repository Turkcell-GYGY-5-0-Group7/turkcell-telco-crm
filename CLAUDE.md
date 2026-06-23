# CLAUDE.md - Telco CRM Operating System

You operate inside the **Telco CRM Platform**: a Java 21 / Spring Boot 4.x microservices system
using CQRS + Mediator, an event-driven Kafka/Avro backbone, PostgreSQL-per-service, Redis, and
Kubernetes. This file is a **router**, not a rulebook: it points to the authoritative sources and
states only the rules that have no better home. Do not restate detailed rules here - link them.

## Authoritative Sources (read these; do not duplicate them)

| Topic | Source of truth |
| --- | --- |
| Architecture decisions | `architecture/adr/ADR-*.md` (21 ADRs) |
| Global build/code conventions | `docs/tasks/README.md` Section 3 |
| Platform rules and layering | `.claude/context/*.md`, `platform/PLATFORM-SPEC.md` |
| Platform capabilities (reuse-before-build) | `docs/architecture/platform-capabilities.md` |
| Service catalog, modes, ports | `docs/architecture/service-catalog.md` |
| Event catalog | `docs/architecture/event-catalog.md` |
| API contracts | `docs/api-contracts/` |
| Product requirements (FR/NFR/AC) | `docs/product/requirements.md` |
| Executable backlog + live status | `docs/tasks/` and `docs/tasks/STATUS.md` |
| Roadmap (product + epic level) | `docs/product/roadmap.md`, epic rollup in `docs/tasks/STATUS.md` |

If two sources conflict: ADRs win over prose; a tech-lead decision wins over everything; the
`docs/tasks` backlog is authoritative for delivery status.

## Non-Negotiable Rules (the short list)

* Services depend ONLY on platform starters, never on `platform-core` directly (ADR-018).
* Reuse before build: consult `docs/architecture/platform-capabilities.md` before writing common
  infrastructure. Never re-implement `ApiResult`, error types, context, pagination, correlation, or
  masking - they are platform-provided.
* No business logic in controllers; domain operations flow through the mediator (ADR-008).
* Each service declares exactly one architecture mode (ADR-004); no mixed modes without tech-lead.
* External APIs under `/api/v1`; all responses wrapped in `ApiResult<T>` (ADR-015).
* Events are immutable, versioned `domain.event.v1`, Avro + Schema Registry; publish via the outbox,
  consume idempotently via the inbox (ADR-009, ADR-019).
* PII encrypted at rest and masked in logs/telemetry; audit logging where mandated (ADR-011, ADR-021).
* Every request carries `traceId` and `correlationId`; logs are structured (ADR-012).
* No emojis anywhere - code, comments, commits, or docs (ARC-09).

## Agent System

A hierarchy of subagents lives in `.claude/agents/`. Invoke them by name for focused work.

| Agent | Authority | Use for |
| --- | --- | --- |
| `tech-lead` | ROOT (final, cannot be overridden) | conflicts, cross-service/platform-core decisions |
| `product-owner` | Planning | roadmap, sprint/task breakdown, status updates |
| `architecture` | Semi (per-service) | design validation, architecture-mode assignment |
| `platform-engineer` | Semi (platform/) | platform-core internals, starters, autoconfigure |
| `microservice-generator` | Generation | scaffold a new service from the ADR-017 template |
| `domain-engineer` | Semi (service logic) | implement business logic, handlers, domain events |
| `event-integration` | Semi (events) | Avro contracts, Schema Registry, outbox/inbox wiring |
| `security` | Semi (security) | JWT, RBAC, PII encryption/masking, audit, rate limit |
| `observability` | Semi (telemetry) | tracing, metrics, structured logging, dashboards |
| `devops` | Semi (build/deploy) | Maven/BOM, CI/CD, Docker, Kubernetes, infra stack |
| `qa` | Quality gate | unit/integration/contract/acceptance tests, coverage |
| `code-review` | Enforcing (read-only) | ADR-compliance review before commit/PR |

Conflict resolution follows `.claude/context/rule-engine.md`. When uncertain about distributed
impact, escalate to `tech-lead` early - never assume silently.

## Workflow

1. **Plan first.** For any non-trivial task (3+ steps or an architectural decision), enter plan mode
   and write the plan to `docs/tasks/todo.md` with checkable items. If something goes sideways, stop
   and re-plan rather than pushing forward.
2. **Use subagents** to keep the main context clean - offload research, exploration, and parallel
   analysis; one focused task per subagent.
3. **Track status.** Update the owning `docs/tasks` sprint `README.md` and `docs/tasks/STATUS.md`
   together whenever a feature changes state.
4. **Verify before done.** Never mark a task complete without proving it works (tests pass, contract
   returns the specified shape). Ask: "would a staff engineer approve this?"
5. **Capture lessons.** After any correction from the user, append the pattern to
   `docs/tasks/lessons.md` so the same mistake is not repeated. Review it at session start.

## Core Principles

* **Simplicity first** - make each change as small as the problem allows; touch only what is needed.
* **No laziness** - find root causes; no temporary patches; senior-engineer standard.
* **Elegance (balanced)** - for non-trivial changes, pause and ask if there is a cleaner way; skip
  this ceremony for obvious fixes. Do not over-engineer.
* **Autonomous bug fixing** - given a failing test or log, just fix it; do not ask for hand-holding.

## Golden Rule

If uncertain: consult `architecture`, validate with `tech-lead`, and never assume silently.
