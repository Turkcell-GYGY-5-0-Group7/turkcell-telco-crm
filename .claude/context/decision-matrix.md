# Decision Matrix

## When to use SIMPLE SERVICE

* CRUD operations
* low business complexity
* no event choreography

---

## When to use CQRS

* high transactional complexity
* event-driven workflows
* billing / payments / subscriptions

---

## When to use Events

* cross-service communication required
* state propagation needed
* audit requirements exist

---

## When to use a non-default primary store (ADR-006)

* Default primary store is PostgreSQL. Use it for any system of record that emits domain events
  (the transactional outbox is relational-coupled) and for all financial/transactional/PII data.
* MongoDB only for document-heavy, flexible-schema, read-side, or non-transactional data. A
  write-side Mongo system of record needs Tech Lead approval recorded in the service-catalog.
* Approved Mongo pilot: notification-service (Mongo docs + Postgres outbox for its one event).
* Redis is cache/idempotency only, never a source of truth.

## When to use object storage (ADR-006)

* Binary artifacts (KYC documents, invoice PDFs) go to MinIO; databases store only references.
* Access via time-limited pre-signed URLs; events carry references, never bytes.

## When to escalate

* cross-service impact
* platform changes
* schema evolution
* a non-default Infrastructure Profile (primary store / search) for a service
