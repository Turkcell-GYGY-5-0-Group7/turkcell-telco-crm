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

## 2026-06-26 - Dockerizing Spring Boot services in a Maven multi-module repo
- **Builder image must have Maven**: `eclipse-temurin:21-jdk-alpine` has no `mvn`. Use
  `maven:3.9-eclipse-temurin-21-alpine` as the builder stage.
- **Schema Registry unreachable inside Docker build**: the `kafka-schema-registry-maven-plugin`
  runs during `mvn install` and tries to hit `localhost:8081`. It has a built-in skip flag:
  add `-Dschema.registry.skip=true` to the `mvn -f platform/pom.xml install ...` command in
  every Dockerfile.
- **Maven reactor needs all sibling pom.xml files**: a Dockerfile that copies only
  `microservices/SERVICE/pom.xml` causes `mvn -f microservices/pom.xml -pl SERVICE` to fail
  because the parent pom lists all sibling modules. Fix: add `# syntax=docker/dockerfile:1`
  and use `COPY --parents microservices/*/pom.xml ./` to copy every module's pom in one line
  while preserving the directory structure.
- **No curl in Alpine JRE runtime image**: `eclipse-temurin:21-jre-alpine` has no `curl`, so
  compose healthchecks that use `curl` fail silently. Add `RUN apk add --no-cache curl` to the
  runtime stage of every service Dockerfile.
- **Spring Cloud Config does NOT resolve `${...}` server-side**: placeholders in served YAML
  (e.g. `${REDIS_HOST:localhost}`) are forwarded raw to clients, who resolve them with their
  own environment. Hardcoded values in `application-dev.yml` (e.g. `host: 127.0.0.1`) cannot
  be overridden by env vars at all. Fix: introduce a `docker` Spring profile. Create
  `configs/application-docker.yml` and per-service `application-docker.yml` files with Docker
  service-name addresses; run containers with `SPRING_PROFILES_ACTIVE=dev,docker` so the
  `docker` profile overrides the `dev` hardcoded values.

## 2026-06-26 - Spring Security AccessDeniedException is NOT the platform exception

- `GlobalExceptionHandler` in `starter-api` handles `com.telco.platform.common.exception.AccessDeniedException`
  → 403. But `@PreAuthorize` throws `org.springframework.security.access.AccessDeniedException` — a
  completely different type. Without a handler for it, the platform catch-all `handleUnexpected(Exception)`
  fires and returns 500.
- Rule: whenever a service uses `@PreAuthorize` (method-level RBAC), add a service-level
  `@RestControllerAdvice` with `@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)`
  that returns 403. Spring MVC picks the most specific exception handler, so this beats the
  platform catch-all without touching platform code.
- Do NOT try to move RBAC to `authorizeHttpRequests` as a workaround on Spring Boot 4.x /
  Spring Security 7.x — rejection at the filter layer triggers `ExceptionTranslationFilter` which
  may see the authenticated user as anonymous (deferred context issue) and returns 401 instead of 403.

## 2026-06-22 - docs/tasks is the single status source of truth
- Mistake: status lived in two unreconciled places (.claude/roadmap vs docs/tasks).
- Rule: `docs/tasks/` is authoritative for delivery status and program structure (epics/phases live
  in `docs/tasks/STATUS.md`). Update the owning sprint README and `STATUS.md` together. The separate
  `.claude/roadmap` tracker was removed to eliminate the dual-source-of-truth complexity.
