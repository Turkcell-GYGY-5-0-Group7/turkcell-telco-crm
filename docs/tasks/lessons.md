# Lessons

Self-improvement log. After any correction from the user, append the pattern here as a rule that
prevents the same mistake. Review at session start. Keep entries short and actionable.

Format:

```
## YYYY-MM-DD - <short title>
- Mistake: <what went wrong>
- Rule: <what to do instead, every time>
```

---

## 2026-06-23 - propagate ADR changes down to subtasks, not just summaries
- Mistake: after changing ADR-006 (Mongo/MinIO), ADR-011 (Keycloak issues tokens), and adding ADR-022,
  only sprint READMEs and contracts were updated; granular subtask files (Sprint 04/05 auth, Sprint 12
  notification) still described the superseded design, so sprints contradicted themselves.
- Rule: when an ADR decision changes, grep the whole `docs/tasks` tree for the old assumption and
  update every affected subtask file, deliverable, test, and dependency - not just the README. A
  README that says X while its subtasks say not-X is worse than no note. Use the tech-lead agent to
  ratify cross-cutting changes (e.g. ARC-06 REST vs gRPC) and amend the cited ADR, not only the
  requirement.

## 2026-06-24 - Spring Cloud Gateway Server MVC 5.0.1 route configuration
- `HandlerFunctions.http(String)` does not exist; only `HandlerFunctions.http()` (no-arg) is
  available. Put ALL route URIs in YAML under `spring.cloud.gateway.server.webmvc.routes`, not in
  Java config. The correct YAML prefix is `spring.cloud.gateway.server.webmvc` (NOT `.mvc`).
- `optional:configserver:` in `spring.config.import` must be quoted: `"optional:configserver:"` —
  the trailing colon is treated as a YAML mapping key otherwise (SnakeYAML parse error).
- Spring Cloud 2025.1.0 + Spring Boot 4.1.0: add `spring.cloud.compatibility-verifier.enabled: false`
  to ALL services (config-server, discovery-server, gateway, and shared `configs/application.yml`).
- Redis password: Docker Compose starts Redis with `requirepass telco`. Set
  `spring.data.redis.password` to `${REDIS_PASSWORD:telco}` or connections fail.
- Lettuce async DNS on macOS: `localhost` resolves to `<unresolved>` via Netty's async DNS.
  Fix: (1) use `127.0.0.1` instead of `localhost`, and (2) register a `ClientResources` bean with
  `DefaultAddressResolverGroup.INSTANCE` (from `io.netty.resolver`) to use JVM blocking DNS.
- Explicit `JwtDecoder` bean: always define `NimbusJwtDecoder.withJwkSetUri(uri).build()` as an
  explicit `@Bean` — do not rely on Spring Security auto-configuration ordering for this.
- `AuthenticationEntryPoint` correct import: `org.springframework.security.web.AuthenticationEntryPoint`
  (NOT `org.springframework.security.web.authentication.AuthenticationEntryPoint`).
- Rate-limiter must fail-open: wrap Redis execute in try-catch; log WARN and fall through — a Redis
  outage must not bring down the gateway. Also add `.requestMatchers("/error").permitAll()` so
  filter exceptions don't redirect to a secured `/error` endpoint and cascade to 401.

## 2026-06-22 - docs/tasks is the single status source of truth
- Mistake: status lived in two unreconciled places (.claude/roadmap vs docs/tasks).
- Rule: `docs/tasks/` is authoritative for delivery status and program structure (epics/phases live
  in `docs/tasks/STATUS.md`). Update the owning sprint README and `STATUS.md` together. The separate
  `.claude/roadmap` tracker was removed to eliminate the dual-source-of-truth complexity.
