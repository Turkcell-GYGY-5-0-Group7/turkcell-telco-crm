# Telco CRM Platform Documentation

This directory holds the product and architecture documentation for the Telco CRM Platform.
Architecture Decision Records (the technical authority) live in
[`/architecture/adr/`](../architecture/adr/). Execution planning lives in
[`/.claude/roadmap/`](../.claude/roadmap/).

Authority rule: where business documents and ADRs disagree on technical decisions, the
ADRs are authoritative (`CLAUDE.md` Section 3).

## Product

| Document | Purpose |
| --- | --- |
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
| [`/architecture/adr/`](../architecture/adr/) | Architecture Decision Records (ADR-001..020). |

## Data

| Document | Purpose |
| --- | --- |
| [`erd/`](./erd/) | Per-service entity-relationship diagrams (PDF). |

## Reading Order

1. [BRD](./product/BRD.md) for business context and scope.
2. [requirements](./product/requirements.md) for the testable FR/NFR list.
3. [service-catalog](./architecture/service-catalog.md) and [event-catalog](./architecture/event-catalog.md) for the technical map.
4. [roadmap](./product/roadmap.md) and [`/.claude/roadmap/`](../.claude/roadmap/) for delivery sequencing.
