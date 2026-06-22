# Platform Rules

## Dependency Rules

* Microservices may ONLY use platform-starters
* platform-core is forbidden for services

---

## CQRS Rules

* All commands go through mediator
* No direct handler invocation outside mediator

---

## Outbox/Inbox Rules

* All events MUST go through outbox
* Inbox must ensure idempotency

---

## Spring Rules

* Auto-configuration only via starters
* No manual bean duplication
