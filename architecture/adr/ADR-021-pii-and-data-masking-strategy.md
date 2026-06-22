# ADR-021 PII and Data-Masking Strategy

Status: Accepted
Date: 2026-06-22

---

## Context

The Telco CRM platform processes Turkish telecommunications customer data, which includes
regulated personal identifiers:

* TCKN (Turkish national identity number, 11 digits)
* MSISDN / GSM phone numbers
* IBAN and payment card numbers (PAN)
* Email and postal addresses
* Names and dates of birth

This data flows through every observability and persistence surface:

* Structured logs shipped to Loki (ADR-012)
* Persisted exception logs (`exception_logs.message`, `exception_logs.stack_trace`)
* Persisted request logs (`request_logs`)
* Outbox event payloads (`outbox_event`) and Kafka/Avro messages (ADR-005, ADR-009)
* Developer-authored log statements and record `toString()` output

KVKK (the Turkish data-protection law) and platform security obligations (ADR-011) require that
personal data is not exposed in operational telemetry. A leak in a log line is as serious as a
leak in an API response, and is harder to detect after the fact.

### Current state

* `LoggingBehavior` and `Slf4jRequestLogWriter` log request **metadata only** (type, kind, userId,
  correlationId, duration, success, errorCode). They do not serialize payloads today.
* A `NotLoggable` marker interface exists but is all-or-nothing: it suppresses the entire log entry
  for a request, with no field-level control.
* `ExceptionLogEntry.message` and `stackTrace` can embed identifiers and are persisted and logged.
* Outbox/Avro event payloads carry full domain data and are intentionally unmasked on the wire.

There is no consistent, declarative mechanism to mark a field as sensitive, and no defense-in-depth
backstop for free-text leakage. We need one.

---

## Decision

We will adopt a **layered, opt-in-by-annotation data-masking strategy** for all operational
telemetry, governed centrally by the platform under `telco.platform.logging.masking.*`.

Masking applies to the **log/persistence view** of data only. The **wire view** (Kafka events,
Avro payloads, HTTP API responses) is never altered by masking; it is governed by transport
security (ADR-011) and event contracts (ADR-009, ADR-019). This separation is a hard rule.

---

# 1. Two-Layer Masking Model

### Layer A — Annotation-driven structured masking (primary)

* A field-level annotation `@Sensitive` is introduced in `platform-common`.
* A dedicated **masking `ObjectMapper`** honors `@Sensitive` when serializing objects for logs or
  log persistence. The default application `ObjectMapper` (used by event serialization and HTTP
  responses) ignores the annotation and emits real values.
* Masking strategies (per-field, via the annotation):
  * `PARTIAL` — keep the last N characters, mask the rest (e.g. `*******8901`). **Default.**
  * `FULL` — full redaction (`*****`).
  * `HASH` — stable SHA-256 digest, allowing correlation without exposure.
  * `EMAIL` — local/domain-aware partial mask (e.g. `a***@***.com`).

### Layer B — Regex backstop in the log appender (defense-in-depth)

* A Logback message converter masks well-known Turkish PII formats in rendered log text
  regardless of annotations: TCKN, MSISDN, IBAN (`TR...`), PAN, email.
* This catches exception messages, third-party log output, and developer free-text that Layer A
  cannot reach structurally.

Both layers are enabled together in production.

---

# 2. Default Masking Behavior

* Default strategy for unspecified `@Sensitive` fields: **`PARTIAL`, keep last 4**.
* Default mask character: `*`.
* Masking is **enabled by default** (`telco.platform.logging.masking.enabled=true`).
* Request/response body logging is **disabled by default** and is gated behind masking when
  explicitly enabled.

---

# 3. Configuration Surface

All knobs live under `telco.platform.logging.masking.*`, consistent with ADR-018/ADR-020.

```yaml
telco:
  platform:
    logging:
      masking:
        enabled: true
        default-strategy: PARTIAL      # FULL | PARTIAL | HASH | EMAIL
        mask-char: "*"
        keep-last: 4
        patterns:                      # Layer B regex backstop
          tckn: true
          msisdn: true
          iban: true
          email: true
          pan: true
      payloads:
        log-request-bodies: false      # explicit opt-in; always masked when on
```

---

# 4. Scope of Application

Masking MUST be applied at these surfaces:

* Mediator request logging (`LoggingBehavior` / `RequestLogWriter` implementations).
* Persisted request logs and exception logs (`starter-log-persistence`), including exception
  `message` and `stackTrace`.
* Any developer log statement that serializes a domain object through the masking `ObjectMapper`.

Masking MUST NOT be applied at:

* Outbox event payloads and Kafka/Avro messages.
* HTTP API responses (`ApiResult<T>`).
* The service's own database tables (domain storage).

---

# 5. Developer Rules

* Mark every PII field on commands, queries, DTOs, and exception-carried data with `@Sensitive`.
* Prefer `@Sensitive` over `NotLoggable`; reserve `NotLoggable` for requests whose metadata itself
  is sensitive.
* Never log raw domain identifiers via string concatenation; rely on the masking `ObjectMapper`.
* Exception messages MUST NOT embed raw identifiers; reference resources by surrogate id where
  possible.

---

## Consequences

### Positive

* Consistent, declarative PII protection across all telemetry.
* Clean separation of log view (masked) from wire view (unmasked, contract-governed).
* Defense-in-depth: structured masking plus a regex backstop for free text.
* KVKK-aligned operational posture.

### Negative

* Requires developer discipline to annotate fields; gaps are mitigated, not eliminated, by Layer B.
* Two `ObjectMapper` beans increase configuration surface and require careful injection.
* Regex backstop carries slight per-log-line overhead and a risk of over/under-masking.

---

## Alternatives Considered

### Single global masking ObjectMapper

Rejected: it would also mask event payloads and API responses, breaking event contracts.

### Regex-only masking

Rejected: coarse, cannot apply field-specific strategies, and risks both false positives and
missed structured fields.

### No annotation, rely on NotLoggable

Rejected: all-or-nothing suppression loses useful debugging metadata and does not address
exception-message or persisted-log leakage.

---

## Related ADRs

* ADR-011 Security Foundation
* ADR-012 Observability Strategy
* ADR-009 Event Driven Architecture
* ADR-018 Platform Starter Dependency Model
* ADR-020 Platform Maven Architecture
