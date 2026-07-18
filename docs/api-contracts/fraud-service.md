# fraud-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9013 |
| Mode | CQRS + Mediator |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 23](../tasks/sprint-23-sim-swap-fraud/README.md) |
| Build status | TODO |
| Requirements | Scope defined by [ADR-029](../../architecture/adr/ADR-029-fraud-detection-mvp-scope.md) and `docs/tasks/sprint-23-sim-swap-fraud/design-note.md`; no FR/AC IDs assigned in `docs/product/requirements.md` yet. |

Bounded context: rule-based SIM-swap / fraud detection reacting to existing subscription-service
domain events (ADR-029). Owns `MsisdnLifecycleSignal`, `FraudRule`, `FraudSignal`, and `FraudCase`
in its own `fraud-db` (PostgreSQL, database-per-service, ADR-006). **Read-only relative to
subscription-service** (ADR-029 Section 1): consumes its events via the inbox and never accesses
`subscription-db` directly.

Rule evaluation and the inbox consumers (23.2) and the fraud-case/rule-config API (23.3) are
implemented; the outbox event publishers land alongside 23.4. Detect-and-alert only - fraud-service
never automatically suspends a subscription (ADR-029 Section 5); resolving a `CONFIRMED` case is a
`FraudCase` status change plus an event publish only.

## Authentication and Authorization

- `/api/v1/fraud-cases/**` and `/api/v1/fraud-rules/**` require a valid JWT, like every other CQRS +
  Mediator domain service (Feature 23.3). RBAC reuses the platform's existing role taxonomy: `SUPPORT`
  is the agent/fraud-analyst role (the same role ticket-service gates its agent assign/resolve
  endpoints on) and `ADMIN` is the stricter operations role.
- `/internal/**` is reserved for any future trusted system-to-system surface (tokenless,
  network-perimeter trust behind the gateway `internal-deny-route`). None defined yet.

All responses are wrapped in `ApiResult<T>` (ADR-015); list responses use `PageResult<T>`. A
`@PreAuthorize` rejection returns `403`; an unknown id/rule code returns `404`
(`ResourceNotFoundException`); resolving an already-resolved case returns `422`
(`BusinessRuleException`).

## Endpoints

### Fraud cases (Feature 23.3.1 / 23.3.2)

| Method | Path | Role | Request | Response (`data`) |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/fraud-cases` | `SUPPORT` or `ADMIN` | query params: `status` (optional, one of `OPEN`/`UNDER_REVIEW`/`CONFIRMED`/`DISMISSED`), `customerId` (optional UUID), `page` (default 0), `size` (default 20) | `PageResult<FraudCaseSummaryResponse>` |
| GET | `/api/v1/fraud-cases/{id}` | `SUPPORT` or `ADMIN` | path `id` (UUID) | `FraudCaseDetailResponse` (case + linked signals, each with its contributing `MsisdnLifecycleSignal` `sourceSignalIds`) |
| POST | `/api/v1/fraud-cases/{id}/resolve` | `SUPPORT` or `ADMIN` | `ResolveFraudCaseRequest` | `FraudCaseSummaryResponse` (resolved case) |

`ResolveFraudCaseRequest`:

```json
{ "status": "CONFIRMED", "note": "optional free-text (max 1000 chars)" }
```

`status` must be `CONFIRMED` or `DISMISSED`; any other value is rejected (`422`). Resolving a case
sets `resolvedAt`/`resolvedBy` (the authenticated user id), transitions status, and publishes
`fraud.case-resolved.v1` via the outbox. **It never calls subscription-service and never suspends,
holds, or otherwise mutates a subscription** (ADR-029 Section 5, hard scope boundary).

`FraudCaseSummaryResponse`: `id`, `customerId`, `status`, `openedAt`, `resolvedAt`, `resolvedBy`,
`signalCount`.

`FraudCaseDetailResponse`: `id`, `customerId`, `status`, `openedAt`, `resolvedAt`, `resolvedBy`,
`signals` (list of `FraudSignalResponse`).

`FraudSignalResponse`: `id`, `ruleCode`, `customerId`, `msisdn`, `subscriptionId`, `severity`,
`triggeredAt`, `sourceSignalIds` (contributing `MsisdnLifecycleSignal` ids).

### Fraud rules (Feature 23.3.3)

| Method | Path | Role | Request | Response (`data`) |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/fraud-rules` | `SUPPORT` or `ADMIN` | - | `List<FraudRuleResponse>` (all three fixed rule codes) |
| PUT | `/api/v1/fraud-rules/{code}` | `ADMIN` | `UpdateFraudRuleRequest` | `FraudRuleResponse` (updated rule) |

`{code}` is one of `RAPID_SIM_SWAP`, `MSISDN_CHURN_VELOCITY`, `SUSPEND_REACTIVATE_VELOCITY`; an
unknown/unparseable code returns `404` (adding a genuinely new rule type is a code change, not
config, per ADR-029 Section 4). A `PUT` takes effect on the next rule evaluation with no restart.

`UpdateFraudRuleRequest`:

```json
{ "windowMinutes": 120, "thresholdCount": 5, "severity": "HIGH", "enabled": true }
```

`windowMinutes` and `thresholdCount` must be positive; `severity` is one of `LOW`/`MEDIUM`/`HIGH`.

`FraudRuleResponse`: `code`, `windowMinutes`, `thresholdCount`, `severity`, `enabled`.

## Events

### Consumed (existing, from subscription-service via inbox - wired in Feature 23.2)

_None wired yet. Planned: `msisdn.allocated.v1`, `msisdn.released.v1`, `subscription.activated.v1`,
`subscription.suspended.v1`._

### Published (new, from fraud-service via outbox - wired in Feature 23.4)

_None wired yet. Planned: `fraud.signal-raised.v1`, `fraud.case-opened.v1`,
`fraud.case-resolved.v1`._
