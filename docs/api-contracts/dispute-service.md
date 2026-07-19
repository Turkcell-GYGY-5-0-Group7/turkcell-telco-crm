# dispute-service - API Contract

| Field | Value |
| --- | --- |
| Port | 9012 |
| Mode | Domain Orchestration |
| Base path | `/api/v1` |
| Owning sprint | [Sprint 22](../tasks/sprint-22-dispute-chargeback/README.md) |
| Build status | Features 22.1/22.2/22.3 built (state machine, commands/handlers, API) - not live-verified, no Docker |
| ADR | [ADR-028](../../architecture/adr/ADR-028-dispute-and-chargeback.md) |

Bounded context: invoice dispute / PSP chargeback workflow. Coordinates billing-service and
payment-service exclusively via outbox events (ADR-009/019) - never shared DB access (ADR-006). Audit
and provisional-hold enforcement mandatory (ADR-028 Section 2/5, ADR-021).

## Authentication and Authorization

All endpoints require a valid JWT. `SUBSCRIBER` may open/submit-evidence/withdraw/read only their own
dispute (ownership enforced by comparing the caller's linked customer-service id -
`UserContext.customerId()`, not the raw Keycloak subject - against `Dispute.customerId`).
`ADMIN`/`CALL_CENTER_AGENT` may resolve any dispute; `ADMIN` may act on any dispute at all.

## Endpoints

| Method | Path | Auth | Summary |
| --- | --- | --- | --- |
| POST | `/api/v1/disputes` | `SUBSCRIBER`/`ADMIN` | Open a dispute against an invoice and/or payment; moves straight to `UNDER_REVIEW`. |
| POST | `/api/v1/disputes/{id}/evidence/upload` | `SUBSCRIBER`/`ADMIN` | Multipart upload; stores the file in MinIO and records the evidence row in one call. |
| GET | `/api/v1/disputes/{id}/evidence/{evidenceId}/download-url` | `SUBSCRIBER`/`ADMIN` | Time-limited (15 min) presigned MinIO download URL. |
| POST | `/api/v1/disputes/{id}/resolve` | `ADMIN`/`CALL_CENTER_AGENT` | Body `{outcome: CUSTOMER\|MERCHANT, resolutionAmount?}`. `CUSTOMER` requires `resolutionAmount`. |
| POST | `/api/v1/disputes/{id}/withdraw` | `SUBSCRIBER`/`ADMIN` | Customer withdraws their own dispute. |
| GET | `/api/v1/disputes/{id}` | `SUBSCRIBER`/`ADMIN` | Fetch a dispute, its evidence, and its full state-transition history. |
| GET | `/api/v1/disputes?customerId=...` | `SUBSCRIBER`/`ADMIN` | Paginated list; a non-admin caller is always scoped to their own customer id. |

## Events

| Direction | Event |
| --- | --- |
| Publish | `dispute.opened.v1`, `dispute.evidence-submitted.v1`, `dispute.resolved-customer.v1`, `dispute.resolved-merchant.v1`, `dispute.withdrawn.v1`, `dispute.closed.v1` |
| Consume | none (producer-only this sprint; billing/payment/ticket-service consume in Features 22.4/22.5/22.6) |

## Notes

- **Provisional-hold invariant (ADR-028 Section 5)**: `dispute.opened.v1` is exclusively a hold signal;
  only `dispute.resolved-customer.v1` may trigger a real credit/refund, and that action is performed by
  billing-service/payment-service (Features 22.4/22.5), never by dispute-service itself.
- Evidence binaries are never persisted in `dispute-db` - only the MinIO object reference (`objectRef`).
- Every state-changing endpoint writes exactly one `audit_log` row (ADR-021, NFR-12).
- `CloseDisputeCommand` exists (Feature 22.2) but is not exposed via this API - closure is triggered once
  a resolution's downstream action is confirmed, a later feature's concern.
- Not yet built: the API surface has not been live-verified against a real cluster (no Docker this
  session) - `@PreAuthorize`/security-filter-chain behavior and the MinIO upload/download round trip are
  reviewed and unit-tested at the component level, not exercised end to end.
