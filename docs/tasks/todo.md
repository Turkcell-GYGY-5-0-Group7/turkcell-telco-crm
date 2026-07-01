# Working TODO

Scratchpad for the current task. Write the plan here before implementing, check items off as you go,
and add a short review section when done. This is ephemeral working state - durable status lives in
the sprint READMEs and `STATUS.md`.

## Current task: Sprint 12 reconciliation (Notifications & Ticketing)

Backlog said Sprint 12 = TODO 0/6, but notification-service + ticket-service are already coded
(committed inside the messy `10a5d26 sprint-10` commit, alongside billing). qa audit + tech-lead
ruling found Sprint 12 is NOT done and AC-01/02/03 are currently DEAD.

### Key ruling (tech-lead, binding)
- Platform emits JSON over Debezium EventRouter to `<domain>.events` topics; event type is in the
  `eventType` Kafka HEADER. Avro/Schema Registry is in ADR-009 but UNIMPLEMENTED platform-wide.
- notification-service consumers subscribe to non-existent `<event>.v1` topics -> zero delivery.
  Fix = align to the live JSON + `.events` + header contract (NOT Avro), mirroring
  subscription-service `PaymentCompletedEventConsumer`.
- billing-service + usage-service publish PascalCase aggregateType ("Invoice"/"Quota") -> route to
  dead `Invoice.events`/`Quota.events`. Must be lowercased for AC-02/AC-03 to work.
- Follow-up debt (NOT this sprint): platform-wide JSON->Avro migration OR amend ADR-009 to bless
  JSON-over-EventRouter. -> product-owner backlog + tech-lead ADR.

### Plan (phases; subagent-driven)

Phase 1 - domain-engineer: eventing correctness (makes AC-01/02/03 actually fire)
- [ ] notification-service consumers: re-topic to `<domain>.events`, filter on `eventType` header;
      consolidate the per-event listeners; keep JSON parsing.
- [ ] notification-service inbox idempotency: key on envelope `eventId`, not `key:offset`.
- [ ] billing-service: lowercase outbox routing aggregateType ("Invoice" -> "invoice") in all emitters.
- [ ] usage-service: lowercase outbox routing aggregateType ("Quota" -> agreed domain) in MeterCdr.
- [ ] reconcile final topic-per-domain map with docs/architecture/event-catalog.md + notification subs.

Phase 2 - domain-engineer: remaining Sprint 12 domain gaps
- [ ] ticket-service SLA-breach ONCE: add `sla_breached_at` (flag/column), exclude from `findBreached`.
- [ ] ticket-service: drop hand-rolled `outbox_events`/`inbox_messages` from `V1__ticket.sql`;
      rely on platform `V900/V901` (as notification-service does).
- [ ] notification-service history endpoint: return paginated shape (page/total), not bare list.
- [ ] notification-service: adapter resilience wrapping (NFR-10) + FAILED-status path.

Phase 3 - qa: real tests (close the fidelity gap that hid all of the above)
- [ ] notification integration test: real Mongo + Kafka Testcontainers; drive real consumers for
      AC-01 (subscription.activated), AC-02 (invoice.generated), AC-03 (quota threshold/exceeded);
      assert dispatch + preference suppression + inbox dedup.
- [ ] ticket: SLA-breach-emitted-once test (run detection twice, assert single emission); 404 test.
- [ ] notification: FAILED-status test; Flyway/schema assertion parity with ticket.

Phase 4 - code-review: ADR-compliance sweep of notification + ticket (+ billing/usage routing changes).

Phase 5 - me: full reactor build verify; then reconcile STATUS.md + sprint 12 README (and note the
billing/usage routing correction touching sprints 10/11). Log the Avro follow-up debt to product-owner.

### Notes
- Cross-service scope (billing/usage) is mandated by tech-lead as prerequisite for AC-02/AC-03.
- Sprints 10 & 11 are marked DONE but share the routing bug; flag for their own reconciliation.
