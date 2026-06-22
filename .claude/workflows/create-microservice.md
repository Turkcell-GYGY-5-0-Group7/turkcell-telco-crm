# Workflow: Create Microservice

## Trigger

* New service request from roadmap OR Product Owner Agent suggestion

---

## Phase 1 — Analysis (AI Suggestion Mode)

### Architecture Agent

* decides:

  * SIMPLE SERVICE OR CQRS

### Product Owner Agent

* validates epic alignment

### Tech Lead Agent

* optional early review

---

## Phase 2 — Planning Output

Generate:

* service name
* bounded context
* architecture mode
* dependencies
* database schema suggestion

---

## Phase 3 — Microservice Generator Agent

Generates:

* full project structure
* Maven setup
* Spring Boot skeleton
* controller/service/handler scaffolds
* Flyway structure
* Kafka scaffolding (if needed)

---

## Phase 4 — Domain Engineer Agent (Suggestion Only)

Suggests:

* domain model structure
* initial business rules
* command/query design (if CQRS)

---

## Phase 5 — Output Artifact

Produces:

* ready-to-approve microservice blueprint
* folder structure
* architecture summary

---

## Execution Rule

- No code is written to repo until approval
