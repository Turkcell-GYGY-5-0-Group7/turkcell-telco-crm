<h1 align="center">
  <img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=28&duration=3000&pause=1000&color=B8D8B0&center=true&vCenter=true&random=false&width=750&lines=Turkcell+-+Telco+CRM;Java+Spring+Boot+Microservices+Project" alt="Typing SVG" />
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Cloud-2025.1.1-6DB33F?style=flat-square&logo=spring&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-4.2-231F20?style=flat-square&logo=apachekafka&logoColor=white" />
  <img src="https://img.shields.io/badge/Debezium-CDC-FF6826?style=flat-square&logo=redhat&logoColor=white" />
</p>

---

# Telco CRM Platform

An event-driven microservices platform that manages the full subscriber lifecycle for a GSM
operator (the fictional operator "TelcoX"): customer registration and KYC, product and tariff
catalog, ordering, subscription activation, usage and quota tracking, billing, payment,
notification, and customer support ticketing.

The platform is built around a custom CQRS + Mediator framework, a transactional outbox/inbox
for reliable eventing, and a governed hybrid application architecture. It is documented through
Architecture Decision Records, which are the single source of technical truth.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Repository Structure](#repository-structure)
- [Platform Modules](#platform-modules)
- [Services](#services)
- [Getting Started](#getting-started)
- [Documentation](#documentation)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)

## Overview

| Aspect | Summary |
| --- | --- |
| Domain | Telecom CRM (subscriber lifecycle, billing, support) |
| Style | Event-driven microservices, database-per-service |
| Application architecture | Hybrid governed modes: Simple, CQRS + Mediator, Domain Orchestration (ADR-004) |
| Messaging | Apache Kafka with Avro and Schema Registry; transactional outbox via Debezium CDC |
| Consistency | Eventual consistency with transactional outbox and idempotent inbox |
| Deployment | Docker for local, Kubernetes for production |
| Authority | Architecture Decision Records in `architecture/adr/` |

The full business context is in the [Business Requirements Document](docs/product/BRD.md).

## Architecture

```text
[ Web / Mobile Client ]
        |
        v
+-----------------+
|   API Gateway   |   JWT validation, rate limit, routing, correlationId injection
+-----------------+
        |
   +-----------------------------+
   | Discovery (dev) | Config    |
   +-----------------------------+
        |
        v   (REST in / gRPC service-to-service)
identity  customer  catalog  order  subscription  usage
   |        |         |        |         |          |
   +--------+---------+--------+---------+----------+
                          |
                     [ Kafka Bus + Schema Registry ]
                          |
   +---------+-----------+-----------+----------+
   |         |           |           |          |
   v         v           v           v          v
 billing   payment   notification  ticket   analytics (future)
```

Key principles (see ADRs for the authoritative rules):

- Each service owns its PostgreSQL schema; no cross-service database access (ADR-006).
- External APIs use `/api/v1` and return `ApiResult<T>` with trace metadata (ADR-015).
- Internal calls prefer gRPC; events are versioned Avro on Kafka (ADR-005, ADR-009, ADR-019).
- A DB write plus an event publish is atomic via the outbox; consumers deduplicate via the inbox (ADR-005).
- Services depend only on platform starters, never on platform-core directly (ADR-018, ADR-020).

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Java 21 (LTS) |
| Framework | Spring Boot 4.1.x, Spring Cloud |
| Build | Maven (multi-module), platform BOM |
| Database | PostgreSQL 17 (per service), Flyway migrations |
| Cache | Redis 8 |
| Messaging | Apache Kafka 4.x (KRaft), Avro, Schema Registry, Debezium CDC |
| Auth | Keycloak (OAuth2 / OIDC), JWT, mTLS internal |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo |
| Resilience | Resilience4j |
| Container / Orchestration | Docker, Kubernetes |
| CI/CD | GitHub Actions |

Authoritative stack: [ADR-003](architecture/adr/ADR-003-technology-stack.md).

## Repository Structure

```text
telco-crm/
├── architecture/adr/      Architecture Decision Records (technical authority)
├── docs/                  Product and architecture documentation
│   ├── product/           BRD, requirements, roadmap, personas, glossary
│   ├── architecture/      service catalog, event catalog
│   └── erd/               per-service entity-relationship diagrams
├── platform/              Reusable platform (BOM, core modules, starters)
├── .claude/               Platform operating context, rules, execution roadmap
└── .github/               Community health files, templates, CI workflows
```

## Platform Modules

The platform follows a layered Maven architecture (ADR-020). Microservices depend only on
starters; starters wrap framework-agnostic core modules with Spring auto-configuration.

```text
platform-bom  ->  platform-core/*  ->  platform-starters/*  ->  microservices
```

| Layer | Modules | Responsibility |
| --- | --- | --- |
| BOM | `platform-bom` | Centralized dependency and version management |
| Core (no Spring) | `platform-common`, `platform-cqrs`, `platform-mediator`, `platform-outbox`, `platform-inbox` | Framework-agnostic primitives and ports |
| Event contracts | `platform-event-contracts` | Avro schemas and versioned event definitions |
| Starters (Spring) | `starter-api`, `starter-mediator`, `starter-security`, `starter-outbox`, `starter-inbox`, `starter-observability` | Auto-configuration, adapters, configuration properties |

Rule: business logic is forbidden in platform modules; core modules carry no Spring dependency
(ADR-007, ADR-018, ADR-020).

## Services

| Service | Port | Architecture mode |
| --- | --- | --- |
| api-gateway | 8080 | infrastructure |
| discovery-server | 8761 | infrastructure |
| config-server | 8888 | infrastructure |
| identity-service | 9001 | CQRS + Mediator |
| customer-service | 9002 | CQRS + Mediator |
| product-catalog-service | 9003 | CQRS + Mediator |
| order-service | 9004 | Domain Orchestration |
| subscription-service | 9005 | CQRS + Mediator |
| usage-service | 9006 | CQRS + Mediator |
| billing-service | 9007 | Domain Orchestration |
| payment-service | 9008 | Domain Orchestration |
| notification-service | 9009 | Simple Service Layer |
| ticket-service | 9010 | CQRS + Mediator |

Authoritative catalog: [docs/architecture/service-catalog.md](docs/architecture/service-catalog.md).

## Getting Started

Prerequisites: Java 21, Maven, Docker.

```bash
# Build the platform (Maven reactor root is platform/)
cd platform
mvn -B -ntp clean install

# A microservice then consumes platform capabilities via starters, for example:
#   <dependency>
#     <groupId>com.telco.platform</groupId>
#     <artifactId>starter-mediator</artifactId>
#   </dependency>
```

Local infrastructure (PostgreSQL, Kafka, Redis, observability) is provided via Docker Compose
as services are added. See the roadmap for delivery sequencing.

## Documentation

| Topic | Document |
| --- | --- |
| Documentation index | [docs/README.md](docs/README.md) |
| MVP analysis and design brief | [docs/product/TELCO-CRM-MVP.md](docs/product/TELCO-CRM-MVP.md) |
| Enterprise evolution (post-MVP) | [docs/product/TELCO-CRM-ADVANCED.md](docs/product/TELCO-CRM-ADVANCED.md) |
| Business requirements | [docs/product/BRD.md](docs/product/BRD.md) |
| Functional and non-functional requirements | [docs/product/requirements.md](docs/product/requirements.md) |
| Product roadmap | [docs/product/roadmap.md](docs/product/roadmap.md) |
| Service catalog | [docs/architecture/service-catalog.md](docs/architecture/service-catalog.md) |
| Event catalog | [docs/architecture/event-catalog.md](docs/architecture/event-catalog.md) |
| API contracts | [docs/api-contracts/](docs/api-contracts/) |
| Architecture decisions | [architecture/adr/](architecture/adr/) |
| Implementation backlog | [docs/tasks/](docs/tasks/) |
| Delivery status dashboard | [docs/tasks/STATUS.md](docs/tasks/STATUS.md) |

## Roadmap

Delivery is phased: P0 platform foundation, P1 identity and master data, P2 onboarding saga,
P3 revenue cycle, P4 engagement and support, P5 hardening and release. See
[docs/product/roadmap.md](docs/product/roadmap.md) and the live execution status in
[docs/tasks/STATUS.md](docs/tasks/STATUS.md).

## Contributing

Read [.github/CONTRIBUTE.md](.github/CONTRIBUTE.md) and the relevant ADRs before opening a pull
request. All changes must pass the architecture-compliance checklist in the pull request
template. No emojis in code, comments, commits, or documentation.

## Security

Report vulnerabilities privately per [.github/SECURITY.md](.github/SECURITY.md). Do not open a
public issue for security reports.

## License

See the repository owner for licensing terms.

---

<p align="center"><sub>Built with a documentation-first, ADR-governed engineering process.</sub></p>