# ADR-015 API Design Standards

Status: Accepted
Date: 2026-06-19

---

## Context

The Telco CRM platform exposes APIs through:

* API Gateway (external clients)
* BFF layer (frontend aggregation)
* Internal service APIs (gRPC + REST hybrid)

We require a strict API contract standard that ensures:

* Consistent response structures across all services
* Traceability across distributed systems
* Unified error handling
* Scalable pagination strategies for different data sizes
* AI-friendly API predictability

---

## Decision

We adopt a **standardized API contract model** based on:

* Unified `ApiResult<T>` wrapper
* Built-in observability metadata
* Dual pagination strategy (offset + cursor)
* Standardized error model

---

# 1. ApiResult Contract

All REST APIs MUST return:

```java id="a1k9lm"
ApiResult<T>
```

---

## Structure

```json id="r2k9lm"
{
  "success": true,
  "data": {},
  "error": null,
  "meta": {
    "traceId": "string",
    "correlationId": "string",
    "timestamp": "2026-06-19T12:00:00Z",
    "service": "customer-service",
    "path": "/api/v1/customers"
  }
}
```

---

## Meta Field Rules

The `meta` field is REQUIRED for ALL responses.

### Must include:

* traceId (from OpenTelemetry)
* correlationId (request flow tracking)
* timestamp
* service name
* request path

---

# 2. Error Model Standard

When `success = false`:

```json id="e1k9lm"
{
  "success": false,
  "data": null,
  "error": {
    "code": "CUSTOMER_NOT_FOUND",
    "message": "Customer not found",
    "details": {},
    "traceId": "abc123"
  },
  "meta": {
    "traceId": "abc123",
    "correlationId": "corr-xyz",
    "timestamp": "2026-06-19T12:00:00Z",
    "service": "customer-service",
    "path": "/api/v1/customers/1"
  }
}
```

---

## Error Rules

* All errors MUST be typed (no generic exceptions)
* traceId MUST be included in error response
* No stack traces exposed externally
* Global exception handler MUST enforce this format

---

# 3. Pagination Strategy (DUAL MODEL)

The system supports two pagination models depending on dataset size.

---

# 3.1 Offset-Based Pagination (DEFAULT)

### Use cases:

* Small to medium datasets
* Admin panels
* Search results
* Non-realtime queries

### Request:

```text id="p1k9lm"
?page=0&size=20
```

### Response:

```json id="p2k9lm"
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

# 3.2 Cursor-Based Pagination (LARGE DATASETS)

### Use cases:

* High-volume datasets
* Event streams
* Logs
* Transactions
* Kafka-backed projections
* Infinite scroll UIs

---

### Request:

```text id="c1k9lm"
?cursor=eyJpZCI6MTIzfQ==&limit=50
```

---

### Response:

```json id="c2k9lm"
{
  "content": [],
  "nextCursor": "eyJpZCI6MTQ1fQ==",
  "hasNext": true,
  "limit": 50
}
```

---

## Cursor Rules

* Cursor MUST be opaque (encoded)
* Cursor MUST NOT expose internal DB structure
* Cursor MUST be stable across service restarts
* Ordering MUST be deterministic (e.g. createdAt + id)

---

# 4. Pagination Selection Rules

Services MUST follow:

| Data Type                | Pagination |
| ------------------------ | ---------- |
| Reference data           | Offset     |
| Admin queries            | Offset     |
| User lists               | Offset     |
| Events / logs            | Cursor     |
| Large tables (>10k rows) | Cursor     |
| Streaming-like data      | Cursor     |

---

# 5. Traceability Rules (CRITICAL)

Every response MUST include:

* traceId (OpenTelemetry)
* correlationId (request flow ID)

These MUST propagate across:

* REST APIs
* gRPC calls
* Kafka events (when user context exists)

---

# 6. DTO Isolation Rule

* ApiResult is ONLY a transport contract
* Domain models MUST NEVER be exposed directly
* Each service defines its own DTO mapping

---

# 7. Internal vs External APIs

| Type                | Format           |
| ------------------- | ---------------- |
| External APIs       | ApiResult + REST |
| Internal APIs       | gRPC preferred   |
| Event communication | Kafka + Avro     |

---

## Consequences

### Positive

* Fully standardized API responses
* End-to-end traceability across system
* Scalable pagination model
* AI-friendly API structure
* Strong debugging capability in distributed systems

### Negative

* Slight response overhead
* Requires strict enforcement in controllers
* More DTO mapping required

---

## Alternatives Considered

### No wrapper (raw REST responses)

Rejected due to inconsistency and missing traceability.

### GraphQL

Rejected due to complexity and mismatch with event-driven backend.

### Single pagination model

Rejected due to inability to scale efficiently.

---

## Related ADRs

* ADR-012 Observability Strategy
* ADR-009 Event Driven Architecture
* ADR-005 Service Communication Strategy
