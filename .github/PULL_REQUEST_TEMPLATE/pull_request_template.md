## Summary

Briefly describe what this PR does and why.

## Related issues

Closes #
Related to #

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor (no functional change)
- [ ] Documentation
- [ ] Build / CI / infrastructure
- [ ] ADR change

## Scope

- Affected service(s):
- Architecture mode (ADR-004): N/A / Simple / CQRS+Mediator / Domain Orchestration
- Requirement linkage (FR/NFR in `docs/product/requirements.md`):
- Roadmap linkage (phase/epic/sprint in `docs/tasks/STATUS.md`):

## Architectural compliance checklist

- [ ] No service depends on platform-core directly (only platform-starters). (ADR-018)
- [ ] External APIs use `/api/v1` and return `ApiResult<T>`. (ADR-015)
- [ ] Events are versioned (`domain.event.v1`) and Avro-schema-driven. (ADR-009, ADR-019)
- [ ] DB write plus event publish is atomic via the outbox; consumers are idempotent. (ADR-005)
- [ ] Controllers contain no business logic; domain is framework-independent. (ADR-004, ADR-008)
- [ ] Requests carry traceId and correlationId; logs are structured. (ADR-012, ADR-015)
- [ ] Flyway migrations included where schema changed. (ADR-016)
- [ ] No emojis in code, comments, commits, or docs. (CLAUDE.md)

## Testing

- [ ] Unit tests added/updated.
- [ ] Integration tests added/updated (Testcontainers where applicable). (ADR-013)
- [ ] Contract tests added/updated (if events or APIs changed).
- [ ] Manual verification performed.

Describe how this was verified:

## Security and data protection

- [ ] No secrets, tokens, or real PII committed.
- [ ] PII fields handled per ADR-011 (encryption, audit log) where relevant.

## Screenshots / logs (optional)

## Notes for reviewers
