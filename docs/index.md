# Telco CRM Platform

Telco CRM Platform is an event-driven microservices system that manages the full subscriber
lifecycle for a GSM operator: customer registration and KYC, product and tariff catalog,
ordering, subscription activation, usage and quota tracking, billing, payment, notification,
customer support ticketing, and (post-MVP) campaign validation, invoice disputes, and fraud
detection.

The reference operator is **TelcoX**, a fictional mobile network operator migrating from a
legacy monolithic CRM to a distributed, cloud-native platform. The platform is built on Java 21
and Spring Boot, uses a database-per-service model on PostgreSQL, communicates asynchronously
over Apache Kafka with an Avro schema registry, and deploys to Kubernetes. Internal application
structure follows a governed hybrid architecture with a custom CQRS and Mediator framework.

This site is the documentation hub. It is a curated set of narrative pages (under `docs/project/`)
that explain the "what" and "why" quickly, and links directly to the authoritative source
documents (Architecture Decision Records, catalogs, API contracts, sprint backlog) for the "how,
in full detail." Where a curated page and an authoritative source disagree, **the authoritative
source wins** - the same rule this repository's `CLAUDE.md` uses internally.

## At a glance

| Aspect | Summary |
| --- | --- |
| Domain | Telecom CRM: subscriber lifecycle, billing, support |
| Style | Event-driven microservices, database-per-service |
| Application architecture | Governed hybrid modes: Simple Service Layer, CQRS + Mediator, Domain Orchestration ([ADR-004](adr/ADR-004-architecture-style.md)) |
| Messaging | Apache Kafka with Avro and Schema Registry; transactional outbox via Debezium CDC |
| Consistency | Eventual consistency, transactional outbox + idempotent inbox |
| Deployment | Docker Compose for local development, Kubernetes (Helm) for production |
| Services | 17 Spring Boot modules: 3 infrastructure + 13 domain services + 1 BFF, plus a SvelteKit web frontend |
| Technical authority | [Architecture Decision Records](adr/ADR-001-repository-strategy.md) (29 ADRs) |

## Technology stack

| Layer | Technology | Version |
| --- | --- | --- |
| Language | Java (LTS) | 21 |
| Framework | Spring Boot, Spring Cloud | 4.1.x, 2025.1.x |
| Build | Maven multi-module reactor + platform BOM | 3.9+ |
| Primary database | PostgreSQL (per service), Flyway migrations | 17 |
| Document store | MongoDB (notification-service; approved ADR-006 exception) | 7.x |
| Cache | Redis | 8 |
| Object storage | MinIO (KYC documents, invoice PDFs, dispute evidence) | - |
| Messaging | Apache Kafka (KRaft, no ZooKeeper) | 4.0.0 |
| Event serialization | Apache Avro + Confluent Schema Registry | 1.12.0 / 7.9.0 |
| Change data capture | Debezium (transactional outbox relay) | 3.1.0 |
| Auth | Keycloak (OAuth2 / OIDC, JWT) | 26.1 |
| Resilience | Resilience4j | 2.3.0 |
| Distributed locking | Redisson (`starter-lock`) | - |
| Secrets | HashiCorp Vault + Secrets Store CSI Driver | - |
| Service mesh | Linkerd (mTLS, edge channel) | - |
| Observability | OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo | - |
| Frontend | SvelteKit (Svelte 5) + TypeScript + Vite | - |
| Testing | JUnit 5, Mockito, Testcontainers, AssertJ | - |
| CI/CD | GitHub Actions, Docker, Helm, Kind (ephemeral cluster) | - |

See [ADR-003: Technology Stack](adr/ADR-003-technology-stack.md) for the authoritative,
versioned list.

## High-level architecture

```text
[ Web (SvelteKit) / Postman / Mobile-shaped clients ]
        |
        v
+------------------+
|   API Gateway    |   JWT validation, rate limiting, routing, correlationId injection
+------------------+
        |
   +-----------------------------+
   | Discovery (dev) | Config    |
   +-----------------------------+
        |
        v   (REST/OpenFeign internal calls)
identity  customer  catalog  order  subscription  usage  campaign  fraud
   |        |         |        |         |          |        |       |
   +--------+---------+--------+---------+----------+--------+-------+
                          |
                     [ Kafka Bus + Schema Registry ]
                     (transactional outbox -> Debezium -> topics -> inbox)
                          |
   +---------+-----------+-----------+----------+-----------+
   |         |           |           |          |           |
   v         v           v           v          v           v
 billing   payment   notification  ticket   dispute      web-bff
```

Every write that must also raise a domain event goes through the **transactional outbox**: the
database write and the outbox row are committed atomically in one transaction, Debezium tails the
outbox table via PostgreSQL logical replication and republishes each row to Kafka, and consumers
deduplicate through the **inbox** before acting. No service calls the Kafka client API directly.
Full detail: [Architecture Overview](project/architecture-overview.md).

## Where to start

**New engineer, want to run it locally** - [Getting Started](project/getting-started.md), then
[Development Workflow](project/development-workflow.md).

**Understanding the system design** - [Architecture Overview](project/architecture-overview.md),
then [Services](project/services.md) and [Events & Messaging](project/events-and-messaging.md).

**Building a new service or feature** - [Platform & Reuse-Before-Build](project/platform-and-reuse.md),
the [Service Catalog](architecture/service-catalog.md), and the
[Architecture Decision Records](adr/ADR-001-repository-strategy.md) - they are the single
source of technical truth.

**Operating / deploying the platform** - [Deployment & Operations](project/deployment-and-operations.md).

**Product scope and business context** - the
[Business Requirements Document](product/BRD.md) and
[Product Roadmap](product/roadmap.md).

**Current delivery status** - [Roadmap & Status](project/roadmap-and-status.md), backed by the
live [Status Dashboard](tasks/STATUS.md).

## Repository layout

```text
turkcell-telco-crm/
├── Makefile               Root entry point for build, infra, and Postman operations
├── architecture/adr/      Architecture Decision Records (29 ADRs; technical authority)
├── docs/                  This documentation site's source (product, architecture, api-contracts, tasks, erd)
│   └── project/           The curated narrative pages (getting started, architecture, security, ...)
├── infra/                 Local developer infrastructure (Docker Compose stack)
├── microservices/         All Spring Boot services (3 infra + 13 domain + web-bff)
├── platform/              Reusable platform: BOM, framework-agnostic core, Spring starters
├── deploy/                Kubernetes Helm charts, chaos engineering, runbooks
├── frontend/web/          SvelteKit web application
├── postman/               Postman collections, environments, host setup scripts
└── .github/workflows/     CI, deploy, acceptance, and frontend pipelines
```
