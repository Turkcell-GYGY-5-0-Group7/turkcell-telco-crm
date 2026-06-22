# CLAUDE.md - service-template

Service-level operating notes. Inherits the platform rules in the root `CLAUDE.md` and the ADRs.

## Identity

- Architecture Mode: CQRS + MEDIATOR (ADR-004).
- Base package: `com.telco.template` (rename when copied).

## Rules for this service

- Depend ONLY on platform starters (ADR-018). Never import `platform-core` modules directly.
- Controllers are thin: translate HTTP to commands/queries, dispatch via `Mediator`, return
  `ApiResult` via `ApiResponseFactory`. No business logic in controllers (ADR-004).
- Commands and queries are immutable records implementing `Command<R>` / `Query<R>`; one handler
  each (`@Component`), resolved by generics. Queries do not change state (ADR-008).
- Validation belongs on the command/query via Jakarta constraints; the mediator enforces it.
- External APIs use `/api/v1` and return `ApiResult<T>` (ADR-015).
- When adding persistence: use JPA + Flyway (`db/migration` plus `classpath:db/migration/platform`),
  publish events through `starter-outbox`, and guard consumers with `starter-inbox`.
- No emojis anywhere.

## When generating code here

- Put commands/queries/handlers/DTOs under `application/`, controllers under `api/`, JPA entities
  and repositories under `domain/` and `infrastructure/` respectively.
- Add focused unit tests for handlers (no Spring needed).
