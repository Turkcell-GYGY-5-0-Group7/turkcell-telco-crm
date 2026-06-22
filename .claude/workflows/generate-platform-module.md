# Workflow: Generate Platform Module

## Trigger

* Platform Engineer Agent proposes new capability
* Tech Lead requests new framework feature
* Roadmap includes platform evolution task

---

## Purpose

To safely introduce or evolve **platform-level components** such as:

* CQRS engine enhancements
* Mediator pipeline behaviors
* Outbox / Inbox improvements
* New Spring Boot starters
* Observability extensions
* Security abstractions

---

# CRITICAL RULE

This workflow is **STRICTLY PLATFORM-SCOPE ONLY**

It MUST NOT touch:

* microservice business logic
* domain implementation
* service-specific architecture

---

# Phase 1 — Proposal Generation

Platform Engineer Agent produces:

* module name
* responsibility boundary
* affected platform layers
* backward compatibility risk
* dependency impact analysis

---

# Phase 2 — Architecture Validation

Architecture Agent:

* validates alignment with ADRs
* checks CQRS / event system compatibility
* ensures no service-level leakage

Decision:

* APPROVE
* MODIFY
* REJECT

---

# Phase 3 — Tech Lead Review (Mandatory)

Tech Lead Agent:

* final authority check
* ensures system stability
* evaluates cross-platform impact

---

# Phase 4 — Implementation Plan

If approved:

Generate:

* Maven module structure
* package layout
* Spring auto-configuration design (if needed)
* integration points with platform-core

---

# Phase 5 — Output Artifacts

* module design document
* dependency graph impact
* rollout strategy
* version bump plan

---

# Safety Rules

* NO breaking changes without version increment
* NO silent behavior changes in CQRS or outbox
* ALL changes must be backward compatible unless explicitly approved

---

# Outcome

A fully validated platform module design ready for implementation.
