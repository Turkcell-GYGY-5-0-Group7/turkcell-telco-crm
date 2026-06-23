# Sprint 01 - Foundation

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE | 4/4 | 2026-06-22 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

## Objective

Establish the empty-repository foundation: monorepo structure, Maven multi-module build with a
shared BOM, the local infrastructure stack (PostgreSQL, Kafka/KRaft, Schema Registry, Debezium,
Redis, observability), and a CI skeleton. Nothing business-facing ships here; everything downstream
depends on it.

## Included Epics

- Epic 1: Foundation and Build Infrastructure

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 1.1 | Repository and Monorepo Structure | DONE | [1.1-repository-and-monorepo-structure.md](1.1-repository-and-monorepo-structure.md) |
| 1.2 | Maven Multi-Module Build | DONE | [1.2-maven-multi-module-build.md](1.2-maven-multi-module-build.md) |
| 1.3 | Local Infrastructure Stack (Docker Compose) | DONE | [1.3-local-infrastructure-stack-docker-compose.md](1.3-local-infrastructure-stack-docker-compose.md) |
| 1.4 | CI Skeleton | DONE | [1.4-ci-skeleton.md](1.4-ci-skeleton.md) |

## Sprint Deliverables

- Initialized monorepo with `.gitignore`, governance files, and ADR-aligned structure.
- `platform-bom` and root reactor build compiling on JDK 21 with Checkstyle/SpotBugs gates.
- Full local infrastructure stack via `make infra-up` (Postgres per-service DBs, Kafka/KRaft, Schema
  Registry, Debezium Connect, Redis, OTel/Tempo/Loki/Prometheus/Grafana).
- CI skeleton running build, test, static analysis, and dependency scanning.

## Exit Criteria

- `mvn -q -DskipTests validate` passes from repo root.
- `make infra-up` brings every infrastructure service to a healthy state and `make infra-down`
  tears it down cleanly.
- CI runs green on an empty-but-structured commit and fails on a seeded Checkstyle violation.
- No business or platform Java code exists yet; this sprint is foundation only.
</content>
