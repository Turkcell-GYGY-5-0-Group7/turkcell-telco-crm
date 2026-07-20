# dispute-service - Agent Notes

## Identity

- Architecture Mode: **Domain Orchestration** (ADR-004)
- Base package: `com.telco.dispute`
- Infrastructure Profile: PostgreSQL (`dispute-db`) + MinIO (dispute evidence objects - Feature 22.3)
- Audit-mandated (ADR-021, NFR-12) - every state-changing command writes an `audit_log` row.
- Owning ADR: [ADR-028 Dispute and Chargeback](../../architecture/adr/ADR-028-dispute-and-chargeback.md)
- Owning sprint: [Sprint 22](../../docs/tasks/sprint-22-dispute-chargeback/README.md)

## Layout

- `domain/` - `Dispute` (aggregate root, 7-state machine), `DisputeEvidence`, `DisputeStateHistory`
  (child entity, one row per transition), `DisputeStatus` enum, `AuditLog`. Framework-free: no Spring
  imports, no setters, private constructor + static factory, guarded transition methods throwing
  `BusinessRuleException` on an illegal transition.
- `application/command/` - one `Command<R>` record per transition (`OpenDisputeCommand`,
  `SubmitEvidenceCommand`, `ResolveDisputeCustomerCommand`, `ResolveDisputeMerchantCommand`,
  `WithdrawDisputeCommand`, `CloseDisputeCommand`).
- `application/handler/` - one `CommandHandler` per command: load/create aggregate -> domain
  transition -> repository save -> `AuditLogWriter.log(...)` -> `OutboxService.publish(...)`. No
  `@Transactional` - the Mediator's `TransactionBehavior` already wraps the call.
- `application/event/` - the six frozen `dispute.*.v1` event DTOs (plain records; see ADR-028 Section 6
  for the exact field lists - do not change field names/types without re-freezing the contract, since
  22.4/22.5/22.6 build inbox consumers against these).
- `application/AuditLogWriter.java` - mirrors `payment-service`'s exactly.
- `infrastructure/persistence/` - Spring Data JPA repositories only.

## Rules for this service

- Never write to `billing-db` or `payment-db` directly (ADR-006) - all cross-service coordination is
  outbox events only. This service does not depend on `starter-inbox` (producer-only in this sprint).
- `aggregate_type` in every `OutboxService.publish(...)` call is the lowercase literal `"dispute"`;
  `aggregate_id` is always `disputeId` (ADR-028 Section 6 - load-bearing for per-dispute Kafka
  ordering, which the provisional-hold invariant depends on).
- The provisional-hold invariant: `OpenDisputeCommand`/`SubmitEvidenceCommand`/
  `ResolveDisputeMerchantCommand`/`WithdrawDisputeCommand` never perform or trigger a financial
  mutation anywhere in the platform. Only `dispute.resolved-customer.v1` (from
  `ResolveDisputeCustomerCommand`) is a valid trigger for a real credit/refund, and that action is
  performed by billing-service/payment-service (Features 22.4/22.5), never by dispute-service itself.
- Depend only on platform starters, never on `platform-core` directly (ADR-018).
- No business logic in controllers once 22.3 adds the API layer - thin controller, `Mediator`,
  `ApiResult<T>` (ADR-008/ADR-015).
