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

## When to escalate

* cross-service impact
* platform changes
* schema evolution
