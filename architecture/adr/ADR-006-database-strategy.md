# ADR-006 Database Strategy

Status: Accepted
Date: 2026-06-19

## Context

The Telco CRM platform requires persistent storage for multiple bounded contexts such as customers, orders, billing, usage, and notifications.

We need a strategy that:

* Ensures service autonomy
* Prevents cross-service data coupling
* Supports scalability
* Allows polyglot persistence when necessary
* Maintains operational simplicity

## Decision

### Primary Database Model

Each microservice MUST use:

> Database-per-Service model

### Naming Convention

```text id="p4k8w1"
<service-name>-db
```

Example:

* customer-db
* order-db
* billing-db

### Primary Database Technology

* PostgreSQL 17 is the default database

### Secondary Databases

* MongoDB 8.x (exception only)
* Redis (cache, not source of truth)

## MongoDB Usage Rule

MongoDB is allowed ONLY for:

* Document-heavy models
* Flexible schema projections
* Read-optimized views
* Non-transactional data

It MUST NOT be used for:

* Core transactional data
* Financial records
* Orders or billing logic

## Event-Emitting Services Must Use a Relational Primary Store (MVP)

The mandatory transactional outbox + inbox + Debezium CDC (ADR-009, ADR-019, NFR-11) is
relational-coupled: `JdbcOutboxStore`, Flyway `V900`/`V901` platform tables, and Debezium reading
the PostgreSQL WAL. Atomic "DB write + event publish" depends on a relational transaction the outbox
row joins.

Therefore, in the MVP:

* Any service whose **system of record emits domain events** MUST use PostgreSQL as its primary store.
* MongoDB is permitted for read-side projections, non-event-emitting data, or document data whose
  events are routed through a **co-located PostgreSQL outbox** owned by the same service.
* The pilot for the co-located pattern is **notification-service**: document/history data in MongoDB,
  with `notification.dispatched.v1` written via a PostgreSQL outbox (non-atomic across the two stores,
  acceptable for an idempotent, non-financial event).

Funding write-side Mongo support (a Mongo outbox/inbox SPI + a Debezium MongoDB connector) is a
platform-core decision requiring a superseding ADR; it is out of scope for the MVP.

## Object Storage

Binary artifacts (KYC documents, invoice PDFs) MUST be stored in **MinIO** (S3-compatible), never as
blobs in PostgreSQL or MongoDB (no `bytea`, no GridFS for these). Storing blobs in an RDBMS bloats the
WAL that Debezium reads and degrades backup/restore.

* The owning database row stores only a **reference**: object key/URI, bucket, content-type, size,
  checksum, and version/etag.
* Access is brokered by the owning service via **time-limited pre-signed URLs**; clients never receive
  bucket credentials. Buckets are per-domain and private.
* Domain events carry the object reference, never the bytes. Bucket lifecycle/retention is owned by
  the producing service.

## Infrastructure Profile (per-service declaration)

Every service MUST declare an **Infrastructure Profile**, parallel to the ADR-004 architecture mode,
in its own `README.md` and in `docs/architecture/service-catalog.md`. It has four axes:

| Axis | Default | Notes |
| --- | --- | --- |
| Primary store | PostgreSQL 17 | MongoDB only by the exception rules above; requires Tech Lead sign-off recorded in the catalog. |
| Cache | none | Redis where read-heavy or idempotency keys are needed. |
| Search | none | Elasticsearch only for read projections, by approval. |
| Object storage | none | MinIO for binary artifacts (KYC docs, invoice PDFs). |

A non-default **primary store** is an exception that the Architecture Agent proposes and the Tech Lead
approves; Code Review validates each service against its declared profile.

## Cross-Service Data Rule

No service may directly access another service’s database.

Data sharing MUST occur via:

* Kafka events
* gRPC APIs
* REST APIs (external only)

## Migration Strategy

* Flyway is mandatory for all PostgreSQL schemas
* Each service owns its migrations independently

## Consequences

### Positive

* Strong service isolation
* Independent scaling
* Reduced coupling
* Easier evolution of services

### Negative

* Data duplication via events
* More operational overhead

## Alternatives Considered

### Shared Database

Rejected due to tight coupling.

### Polyglot-first approach

Rejected due to operational complexity.

## Related ADRs

* ADR-005 Service Communication Strategy
* ADR-009 Event Driven Architecture
