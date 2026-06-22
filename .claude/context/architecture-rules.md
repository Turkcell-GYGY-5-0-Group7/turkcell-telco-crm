# Architecture Rules

## Core Principles

* Microservices are independent
* Database per service is mandatory
* No cross-service direct DB access
* CQRS is optional based on complexity
* SIMPLE SERVICE is default for CRUD systems

---

## Service Design Rules

* Controllers must not contain business logic
* Domain logic must not depend on infrastructure
* Application layer orchestrates flows

---

## Architecture Decision Model

Allowed modes:

* SIMPLE SERVICE
* CQRS + MEDIATOR

No hybrid allowed unless Tech Lead approves
