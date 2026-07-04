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

## 2026-06-26 - CustomerIntegrationTest patterns for CQRS + Mediator services with security and PII
- Rule: use `@MockitoBean OutboxService` (JDBC outbox, no Kafka) and `@MockitoBean DocumentStorage`
  (MinIO adapter) to boot the full Spring context with only a Testcontainers Postgres. Still provide
  dummy `minio.*` properties so `MinioConfig` can create the `MinioClient` bean without connecting.
- Rule: in `@SpringBootTest(webEnvironment = RANDOM_PORT)` tests, use `DELETE FROM` statements in
  `@BeforeEach` in FK-safe order (`audit_log, documents, addresses, customers`) rather than TRUNCATE,
  which requires listing every table or using CASCADE. This avoids accidentally wiping platform tables.
- Rule: the schema-compat gate stores `.avsc` snapshots in `src/test/resources/avro/` and uses
  Jackson's `ObjectMapper` (already on the classpath) to compare Avro field names against Java record
  components via reflection. No Avro library or Schema Registry is needed for this guard. Update both
  the `.avsc` snapshot and the Java record together whenever the event contract changes.
- Rule: when asserting on a soft-deleted row with a UUID primary key via `JdbcTemplate`, cast the
  string parameter explicitly: `WHERE id = CAST(? AS uuid)`. Plain `?` fails on PostgreSQL because
  the driver cannot infer the UUID type from a `String` parameter.
- Rule: the `@ActiveProfiles("test")` profile resolves to `application-test.yml`; it must disable
  config-server (`spring.cloud.config.enabled=false`, `spring.config.import=""`), Eureka, and
  Spring Cloud compatibility verifier to allow a standalone context boot.

## 2026-07-04 - a real gateway-driven acceptance suite finds bugs that unit/integration tests cannot
- Mistake: nothing. Building `microservices/acceptance-tests` (task 14.1.1) by actually authenticating
  through Keycloak and calling every API as a real caller would, instead of trusting each service's own
  mocked/self-issued-JWT test suite, surfaced 8+ real cross-service bugs that had shipped clean through
  every prior sprint's per-service tests: a tariff lookup that routed by `code` when the caller passed a
  UUID `id`; a payment event missing the `invoiceId` field needed to close the AC-02 pay-invoice loop; a
  quota event missing `customerId` so notifications always went to `"unknown"`; 6 services missing
  `application-docker.yml` entirely; a role check for `CUSTOMER`, a role that never existed anywhere in
  Keycloak (the real role is `SUBSCRIBER`) - baked into 6 controllers, 7 test fixtures, and a Flyway seed
  migration; and a deeper one that the role fix then exposed - `customer-service` never links a
  self-registered `customerId` to the caller's Keycloak subject, so no ownership check anywhere in the
  platform can ever be satisfied by a real end-user token.
- Rule: an acceptance suite that calls through the real gateway with a real IdP token, exercising each
  documented business scenario end-to-end, is not redundant with per-service unit/integration tests -
  it is the only test type that catches drift *between* services (one side of a contract changes,
  the other doesn't) and identity/authz gaps that only manifest when a real, non-admin, non-test-fixture
  principal is used. Budget for it early, not as a final Sprint-14 checkbox once everything else is "done".

## 2026-07-04 - copy-pasted platform-pattern code drifts silently across services
- Mistake: `AuditLogWriter.log()` was copy-pasted into 5 services (identity, customer, subscription,
  payment, order) as a stopgap ahead of a planned platform-starter extraction
  (`docs/architecture/platform-capabilities.md`). `order-service`'s copy was fixed at some point to
  guard `UUID.fromString(rawActorId)` in a try/catch (non-UUID principals, e.g. service accounts or
  test fixtures, fall back to a null `actor_id`); the other 4 copies were never updated to match and
  would throw `IllegalArgumentException` and 500 the entire business transaction whenever the caller's
  JWT subject wasn't UUID-shaped.
- Rule: when N services carry a duplicated, not-yet-platformized helper class (flagged in each
  service's CLAUDE.md as "locally-built, isolated for easy extraction"), a fix landed in one copy must
  be diffed against all N copies before the sprint is called done - a duplicated-by-design class is a
  single logical unit for bugfix purposes even though it lives in N files. Grep for the class name across
  the whole `microservices/` tree, not just the one service you're touching.
