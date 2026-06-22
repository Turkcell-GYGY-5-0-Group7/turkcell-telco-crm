# ADR-002 Monorepo Strategy

Status: Accepted

Date: 2026-06-19

## Context

The platform consists of:

* Shared platform libraries
* Multiple microservices
* Infrastructure definitions
* Common contracts
* Shared architectural standards

The organization requires consistent development standards and simplified dependency management.

## Decision

The platform shall use a Monorepo architecture.

All platform libraries, infrastructure assets, and business services shall reside within a single repository.

Repository layout:

```text
platform/
microservices/
infra/
```

Shared libraries shall be versioned together with the repository.

Services shall consume platform libraries directly from the monorepo build process.

## Consequences

### Positive

* Unified architecture
* Simplified refactoring
* Easier platform evolution
* Better AI context awareness
* Shared CI/CD pipelines

### Negative

* Larger repository size
* Longer CI execution times

## Alternatives Considered

### Polyrepo

Rejected due to dependency management complexity.

### Hybrid Repository Model

Rejected due to operational overhead.

## Related ADRs

* ADR-001 Repository Strategy
* ADR-007 Platform Library Strategy
