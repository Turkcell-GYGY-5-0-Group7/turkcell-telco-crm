# ADR-001 Repository Strategy

Status: Accepted

Date: 2026-06-19

## Context

The Telco CRM platform is expected to evolve into a large-scale distributed system consisting of numerous business services, shared platform libraries, infrastructure components, deployment artifacts, architectural documentation, and AI-assisted development assets.

The repository structure must support:

* Multiple microservices
* Shared platform modules
* Infrastructure-as-Code
* Documentation
* AI-assisted development
* Kubernetes deployment
* Long-term maintainability

The repository should remain understandable to both developers and AI agents.

## Decision

The project shall use a repository structure organized around the following top-level domains:

```text
.
├── .claude/
├── .github/
├── architecture/
├── docs/
├── infra/
├── microservices/
├── platform/
├── postman/
├── scripts/
└── tools/
```

Responsibilities:

### .claude

Contains AI-related assets:

* Agents
* Skills
* Standards
* Templates
* Context files
* Prompt files

### .github

Contains GitHub related assets:

* Workflows
* Issue Templates
* Pull Request Templates
* Discussion Templates
* Code Of Conduct
* Contribute
* Security
* CODEOWNERS

### architecture

Contains architecture documentation:

* ADRs
* Diagrams
* Event Storming artifacts
* Domain maps
* Decisions

### docs

Contains business and operational documentation.

### infra

Contains deployment and operational assets:

* Docker
* Kubernetes
* Helm
* Monitoring
* Messaging
* Databases

### platform

Contains reusable platform libraries shared across services.

### microservices

Contains deployable business services.

## Consequences

### Positive

* Clear separation of responsibilities
* Supports large-scale growth
* AI-friendly structure
* Infrastructure and application concerns remain isolated

### Negative

* Larger repository structure
* More initial setup effort

## Alternatives Considered

### Flat Repository

Rejected due to poor scalability.

### Multiple Repositories

Rejected due to operational complexity and loss of shared visibility.

## Related ADRs

* ADR-002 Monorepo Strategy
* ADR-007 Platform Library Strategy
