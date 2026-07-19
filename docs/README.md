# Telco CRM Platform Documentation

Published documentation site (built from this directory with MkDocs Material):
https://turkcell-gygy-5-0-group7.github.io/turkcell-telco-crm/

This directory holds the product and architecture documentation for the Telco CRM Platform.
Architecture Decision Records (the technical authority) live in
[`/architecture/adr/`](../architecture/adr/). Execution planning and live status live in
[`tasks/`](./tasks/) and [`tasks/STATUS.md`](./tasks/STATUS.md).

Authority rule: where business documents and ADRs disagree on technical decisions, the
ADRs are authoritative (`CLAUDE.md` Section 3).

## Product

| Document | Purpose |
| --- | --- |
| [product/TELCO-CRM-MVP.md](./product/TELCO-CRM-MVP.md) | Canonical MVP analysis and design brief (English), reconciled with the ADRs. |
| [product/TELCO-CRM-MVP-TR.md](./product/TELCO-CRM-MVP-TR.md) | Original Turkish MVP brief, preserved verbatim. |
| [product/TELCO-CRM-ADVANCED.md](./product/TELCO-CRM-ADVANCED.md) | Enterprise/senior-level evolution beyond the MVP (scope-out, scale, security, data). |
| [product/BRD.md](./product/BRD.md) | Business Requirements Document; the why and what. |
| [product/requirements.md](./product/requirements.md) | Functional and non-functional requirements with traceability. |
| [product/roadmap.md](./product/roadmap.md) | Product-level phased roadmap and milestones. |
| [product/personas.md](./product/personas.md) | Actors and personas. |
| [product/glossary.md](./product/glossary.md) | Bilingual domain and architecture glossary. |

## Architecture

| Document | Purpose |
| --- | --- |
| [architecture/service-catalog.md](./architecture/service-catalog.md) | Authoritative service list: ports, contexts, aggregates, modes. |
| [architecture/event-catalog.md](./architecture/event-catalog.md) | Domain event registry, producers/consumers, saga sequences. |
| [architecture/keycloak-and-auth.md](./architecture/keycloak-and-auth.md) | Authentication integration guide: Keycloak as token issuer, realm, clients, validation (ADR-011). |
| [architecture/platform-capabilities.md](./architecture/platform-capabilities.md) | Consumer-facing catalog of reusable platform capabilities (reuse-before-build). |
| [architecture/platform-gap-closing-plan.md](./architecture/platform-gap-closing-plan.md) | Plan for the missing cross-cutting platform modules + tech-lead rulings. |
| [`/architecture/adr/`](../architecture/adr/) | Architecture Decision Records (ADR-001..022). |

## API Contracts

| Document | Purpose |
| --- | --- |
| [`api-contracts/`](./api-contracts/) | Per-service external API contracts plus the global gateway contract (ADR-015). |

## Delivery

| Document | Purpose |
| --- | --- |
| [`tasks/`](./tasks/) | Authoritative implementation backlog (15 sprints). |
| [tasks/STATUS.md](./tasks/STATUS.md) | Cross-sprint live status dashboard. |

## Data

| Document | Purpose |
| --- | --- |
| [`erd/`](./erd/) | Per-service entity-relationship diagrams (PDF). |

## Reading Order

1. [BRD](./product/BRD.md) for business context and scope.
2. [requirements](./product/requirements.md) for the testable FR/NFR list.
3. [service-catalog](./architecture/service-catalog.md) and [event-catalog](./architecture/event-catalog.md) for the technical map.
4. [roadmap](./product/roadmap.md) and [`tasks/STATUS.md`](./tasks/STATUS.md) for delivery sequencing and status.
