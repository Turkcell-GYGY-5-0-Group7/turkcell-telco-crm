# Contributing to the Telco CRM Platform

Thank you for contributing. This platform is governed by Architecture Decision Records and
the operating rules in `CLAUDE.md`. Please read this guide before opening a pull request.

## 1. Ground Rules

- Read `CLAUDE.md` (platform operating rules) and the relevant ADRs in
  `architecture/adr/` before making changes.
- Where any document and an ADR disagree on a technical decision, the ADR is authoritative.
- No emojis in code, comments, commit messages, or documentation.
- Never commit secrets, tokens, or real customer PII (TCKN, card numbers, etc.).

## 2. Project Structure

- `architecture/adr/` - Architecture Decision Records (technical authority).
- `docs/` - product and architecture documentation (BRD, requirements, roadmap, catalogs).
- `.claude/` - platform operating context, rules, and execution roadmap.
- `platform/` - platform-core, platform-bom, and starters.
- `docs/erd/` - per-service entity-relationship diagrams.

## 3. Branching and Commits

- `master` is production-ready. Do not push directly to it.
- Create a feature branch: `feature/<short-description>` or `fix/<short-description>`.
- Use clear, conventional commit messages, for example:
  - `feat(customer-service): add KYC approval command`
  - `fix(payment-service): enforce idempotency on retry`
  - `docs(product): expand BRD acceptance criteria`
- A pull request is required for all merges. At least one review is required.

## 4. Architecture Compliance

Every change must satisfy the checklist in the pull request template, including:

- Services depend only on platform-starters, never on platform-core directly (ADR-018).
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- Events are versioned (`domain.event.v1`) and Avro-schema-driven (ADR-009, ADR-019).
- DB write plus event publish is atomic via the outbox; consumers are idempotent (ADR-005).
- Each service declares one architecture mode (ADR-004) in its `README.md`.
- Flyway migrations accompany schema changes (ADR-016).

## 5. Testing

- Unit tests are mandatory.
- Integration tests (Testcontainers where applicable) are mandatory for merge (ADR-013).
- Contract tests are required when events or external APIs change.
- The CI pipeline runs build, test, static analysis, and security scan (ADR-014).

## 6. Local Development

- Java 21 and Maven are required.
- Use the platform BOM for dependency consistency.
- Use Docker Compose for local infrastructure (PostgreSQL, Kafka, Redis, observability stack).

## 7. Opening a Pull Request

1. Ensure the build and tests pass locally.
2. Fill in the pull request template completely, including the compliance checklist.
3. Link the related issue and the relevant FR/NFR and roadmap entry.
4. Request review from a code owner.

## 8. Reporting Issues and Security

- Use the issue templates for bugs and feature requests.
- Report security vulnerabilities privately per `SECURITY.md`. Do not open a public issue.

## 9. Code of Conduct

All contributors must follow the `CODE_OF_CONDUCT.md`.
