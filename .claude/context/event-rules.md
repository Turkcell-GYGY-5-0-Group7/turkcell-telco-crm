# Event Rules

## Naming Convention

domain.event.v1

Example:

* customer.created.v1
* order.paid.v1

---

## Schema Rules

* Avro is mandatory
* Schema registry required
* Backward compatibility required

---

## Delivery Rules

* At least once delivery
* Inbox ensures idempotency
* Outbox ensures consistency
