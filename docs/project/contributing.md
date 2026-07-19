# Contributing

Full guide: [`.github/CONTRIBUTE.md`](https://github.com/turkcell-gygy-5-0-group7/turkcell-telco-crm/blob/master/.github/CONTRIBUTE.md).
This page is the condensed version.

## Ground rules

- Read `CLAUDE.md` and the relevant ADRs before making changes. Where any document and an ADR
  disagree on a technical decision, the ADR wins.
- No emojis anywhere: code, comments, commit messages, documentation.
- Never commit secrets, tokens, or real customer PII (TCKN, card numbers, etc.).

## Branching and commits

- `master` is production-ready; never push to it directly.
- Feature branches: `feature/<short-description>` or `fix/<short-description>`.
- Conventional commit messages, e.g. `feat(customer-service): add KYC approval command`,
  `fix(payment-service): enforce idempotency on retry`.
- A pull request and at least one review are required for every merge.

## Architecture compliance checklist

Every pull request template includes this checklist - satisfy it before requesting review:

- No service depends on `platform-core` directly (only platform starters) - ADR-018.
- External APIs use `/api/v1` and return `ApiResult<T>` - ADR-015.
- Events are versioned (`domain.event.v1`) and Avro-schema-driven - ADR-009, ADR-019.
- A DB write plus its event publish is atomic via the outbox; consumers are idempotent - ADR-005.
- Controllers contain no business logic; the domain layer is framework-independent - ADR-004,
  ADR-008.
- Requests carry `traceId` and `correlationId`; logs are structured - ADR-012, ADR-015.
- Flyway migrations accompany any schema change - ADR-016.
- Each service's `README.md` declares its architecture mode - ADR-004.

## Testing expectations

Unit tests are mandatory on every change. Integration tests (Testcontainers where applicable) are
mandatory to merge. Contract tests are required whenever an event or external API changes. CI
runs build, test, static analysis (Checkstyle/SpotBugs), and a CodeQL security scan on every push
and pull request - see [Deployment & Operations](deployment-and-operations.md#cicd-github-actions).

## Opening a pull request

1. Confirm the build and tests pass locally (`make build && make test`).
2. Fill in the pull request template completely, including the compliance checklist.
3. Link the related issue and the relevant FR/NFR (`docs/product/requirements.md`) and roadmap
   entry (`docs/tasks/STATUS.md`).
4. Request review from a code owner.

## Reporting issues and security

Use the repository's issue templates for bugs and feature requests. Report security
vulnerabilities privately per `.github/SECURITY.md` - never open a public issue for a security
report.

## Previewing this documentation site locally

```bash
pip install -r docs/requirements.txt
mkdocs serve
```

Opens a live-reloading preview at `http://127.0.0.1:8000`. Run this from the repository root -
`mkdocs.yml` treats the repo root as the documentation source (so every existing relative link in
`docs/` and `architecture/adr/` resolves exactly as it does when those files are browsed directly
on GitHub) and excludes the non-documentation directories (`microservices/`, `platform/`,
`frontend/`, `infra/`, `deploy/`, `postman/`, `.claude/`, `.github/`) from the build.

## Code of conduct

All contributors follow `.github/CODE_OF_CONDUCT.md`.
