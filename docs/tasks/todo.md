# Completing Sprint 16 — the live local E2E  [DONE 2026-07-13 — EXIT CRITERION MET]

Goal: discharge the ONE open Sprint 16 exit criterion — "a user logs in via Keycloak (PKCE), completes
onboarding through the UI, and views subscription, usage, and a downloadable invoice PDF" — on the LOCAL
Docker Compose stack (nothing is deployed anywhere; "live" = real running services on localhost).
Sprint 16 code was committed + pushed (d8422f5, branch feat/sprint16-web-frontend) BEFORE this run.

## OUTCOME (2026-07-13): criterion MET. Sprint 16 is DONE (5/5).

A human ran the whole flow in a real browser against the live stack: Keycloak PKCE login -> onboarding
wizard (register -> KYC -> tariff -> review -> place order) -> real saga (order FULFILLED, subscription
ACTIVE, MSISDN 905320000006) -> dashboard -> /account (0/20480 MB, 0/1000 min, 0/500 SMS) -> bill-run ->
/invoices -> invoice PDF DOWNLOADED. Self-scoping proven on real data (bill-run issued 7 invoices; the
user's /invoices returned exactly 1 — their own). Final suites: frontend 153 tests + svelte-check 0 errors
+ lint/build clean; web-bff 31; customer-service 95; api-gateway 9 (all JaCoCo passing).

**The run found 11 real defects that every offline suite missed** — several made the shipped web channel
COMPLETELY NON-FUNCTIONAL in a browser, and the already-pushed d8422f5 contains that broken code. Root
cause: each layer was tested against ITS OWN MOCK (frontend mocks `fetch`; web-bff mocks the gateway), so
a contract mismatch BETWEEN two independently-mocked layers is invisible offline; and 3 defects were
reachable ONLY by a human in a browser (a password-grant API E2E would have sailed past them). 9 fixed:
frontend/BFF onboarding contract drift (tariffId/addonIds + customer{fullName,...} vs the BFF's real
tariffCode/addonCodes + CustomerRegistration{type,firstName,lastName,identityNumber,dateOfBirth}); Tariff
`tariffId` vs the catalog's `code`; getOrderStatus parsing a flat object instead of the ADR-015
`ApiResult<OrderResponse>` (field `id`), so polling could NEVER terminate; the wizard calling the
ADMIN-only `POST /api/v1/payments` (subscriber = 403, admin = DOUBLE CHARGE) — payment step REMOVED, since
charges are event-driven off order.created.v1 (verified live: placing the order alone drives order ->
payment -> subscription -> FULFILLED); the status classifier using the wrong enum (real:
PENDING/CONFIRMED/FULFILLED/CANCELLED/FAILED, only FULFILLED = activated); gateway CORS missing
`Idempotency-Key` (preflight blocked both POSTs; confirmed live); customer-service GET/PUT
/api/v1/customers/{id} still staff-gated from Sprint 14, 403-ing the record's own OWNER (self-ownership
check applied; note `#id.toString()` — SpEL comparing a UUID path var to the String customerId claim
silently evaluates false = deny-all); identity-service wrongly excluded from the E2E subset (it consumes
customer.registered.v1 and writes customer_id back to Keycloak = the source of the `customerId` claim);
and the three browser-only ones — Keycloak rejecting explicitly-named DEFAULT client scopes ("Invalid
scopes: openid profile email" -> request only `openid`), the newly-signed-up-user 403 rendered as a red
error instead of an onboarding CTA (now a recognised state + session refresh for the stale-token race),
and the KYC upload having NO size limit (a 2-8 MB phone photo blew past Spring's inherited 1 MB default
AFTER the customer was already registered -> now capped in the browser at 5 MiB, rejected by web-bff with
400 BEFORE registration, and customer-service multipart limits set explicitly to 6MB/8MB).

### Tracked follow-ups — OPEN, deliberately not fixed (do not read these as done)
- [ ] Duplicate TCKN registration returns 500 instead of a clean 409 Conflict (customer-service).
- [ ] ANY oversized multipart returns 500, not 413: starter-api's GlobalExceptionHandler catches Exception
      before Spring's own 413 mapping — platform-starter issue (platform-engineer / tech-lead).
- [ ] `POST /api/v1/addons` is documented in docs/api-contracts/product-catalog-service.md but is NOT
      implemented (AddonController has only a GET) and returns 500. Pre-existing Sprint 07 gap. Addons are
      optional in the wizard so it did not block the E2E — but the addon selection path is UNPROVEN E2E.
- Customer status stays PENDING after onboarding (KYC approval is a separate admin step) — expected, not a bug.

### Infrastructure reality uncovered (all fixed)
- web-bff was ABSENT from compose, had no application-docker.yml, and had the one un-hardened Dockerfile.
- The full 23-container stack OOM-HUNG the Docker engine: the real ceiling is Docker Desktop's Linux VM
  (measured 7.61 GiB), not the 15.7 GB host, and every JVM was sizing its heap from HOST RAM. All JVMs now
  carry an explicit heap cap; the E2E runs on a documented 18-container subset. schema-registry proved NOT
  needed at runtime (all Debezium connectors use JsonConverter; ADR-019's JSON-outbox amendment).
- register-connectors.sh called `python3` (absent on this machine), and registering all 11 connectors
  against a partial stack aborts. Both fixed.

## Prerequisite gaps found (2026-07-13) and closed
- web-bff was ABSENT from infra/docker/compose.yml -> added (apps profile, port 9020, depends on
  config-server/discovery-server/api-gateway; it is stateless so it does NOT wait on postgres/kafka).
- configs/web-bff/application-docker.yml did NOT exist -> created (gateway base-url -> http://api-gateway:8080,
  Keycloak docker addresses). Without it the dev profile's localhost values would have leaked into the
  container (the exact class of bug Sprint 14 hit on 6 services).
- api-gateway was profiles:[platform] only -> added `apps` (web-bff depends on it), matching the existing
  config-server/discovery-server precedent.
- web-bff Dockerfile was the ONE service Sprint 15 never hardened -> numeric USER 10001 + HEALTHCHECK added.
- infra/docker/.env did not exist -> created from .env.example with a generated ENCRYPT_KEY (gitignored).
- NO telco-*:local images existed (only telco/*:kind from Sprint 15) -> all 14 service images must build.

## Execution order  [all complete]
- [x] Wire web-bff into compose + application-docker.yml + Dockerfile (devops). compose config validates.
- [x] Build all 14 service images.
- [x] Bring up the stack and reach healthy — NOT the full stack: the 23-container stack OOM-hung the Docker
      engine (Docker Desktop's Linux VM ceiling is 7.61 GiB), so this runs on a documented 18-container
      subset with explicit per-JVM heap caps. schema-registry is not needed at runtime.
- [x] Register the Debezium outbox connectors — after fixing register-connectors.sh (`python3` does not
      exist on this machine; registering all 11 against a partial stack aborts).
- [x] API-level live E2E through the gateway with a REAL Keycloak token. NOTE the deviation: the planned
      `POST /api/v1/payments` step was REMOVED, not executed — it is ADMIN-only and charges are event-driven
      off order.created.v1, so calling it was a defect (subscriber = 403; admin = double charge). identity-
      service also had to be ADDED to the subset (it mints the `customerId` claim). CORS preflight with
      Idempotency-Key and self-scoping against a real token both confirmed live.
- [x] BROWSER click-through on the SvelteKit dev server (:3000) — user-driven, the literal "through the UI"
      half of the exit criterion. PASSED 2026-07-13, after fixing 3 browser-only defects it exposed.
- [x] Flip Sprint 16 to fully DONE; STATUS.md + sprint-16 README updated (2026-07-13).

---

# Working TODO — Sprint 16: Web Frontend (post-MVP)

Sprints 01-15 feature-complete (MVP AC-01/02/03 validated live in Sprint 14). Sprint 16 is the first
post-MVP sprint. Mode: plan -> approve -> execute, review at feature boundaries. Update the owning
sprint README + STATUS.md together as each feature reaches DONE; capture lessons.

> **Parked from Sprint 15 (deferred by user choice, non-blocking):** the deployed-environment K8s
> acceptance run — full 13-service in-cluster boot on Kind + Debezium connector registration + the
> AC-01/02/03 run — remains open. It has NO dependency on Sprint 16. Tracked in
> `docs/tasks/sprint-15-deployment/README.md` (Exit-Criteria Follow-Ups) and `docs/tasks/STATUS.md`.
> The schema-registry `enableServiceLinks:false` fix is committed on branch
> `fix/sprint15-schema-registry-service-links` (1 commit ahead of master, not yet PR'd).

Objective: deliver the first end-to-end web channel — a SvelteKit app (`frontend/web/`) plus the
`web-bff` composition service (`/bff/v1/**`, port 9020, Simple Service Layer, stateless). A user logs
in via Keycloak (Authorization Code + PKCE, `telco-web` client), completes onboarding
(register -> KYC -> order -> pay -> activate), and views account data (subscriptions, usage/quota,
invoice PDFs). The browser NEVER calls a domain service directly (ADR-011). Built per ADR-022
(SvelteKit + Svelte 5 + TypeScript) — already Accepted, no ADR gate.

## Decisions (confirmed 2026-07-12)

1. Frontend owner: **domain-engineer** agent (no dedicated frontend agent exists).
2. Toolchain: **Node 20 LTS + npm**, standalone path-filtered CI job, `frontend/web/` **excluded from
   the Maven reactor**.
3. Branch: **`feat/sprint16-web-frontend`** off master (created). Sprint-15 fix branch PR'd separately.
4. web-bff is already scaffolded (`microservices/web-bff/`, port 9020, Simple Service Layer,
   stateless / no primary store) — 16.1 fleshes out the skeleton, not a fresh ADR-017 scaffold.
5. web-bff returns UI DTOs, NOT `ApiResult<T>` (documented ADR-015 BFF exception).

## Tech-lead rulings (RESOLVED 2026-07-12 — all three APPROVED)

1. Gateway routing + CORS (16.1.3) — APPROVED-WITH-CONDITIONS. Add ONE route to
   `microservices/configs/api-gateway/application.yml` under
   `spring.cloud.gateway.server.webmvc.routes`, in the "Domain services" block, mirroring the existing
   `lb://` routes: `id: web-bff-route`, `uri: lb://web-bff`, `predicates: [Path=/bff/v1/**]`. No custom
   filters. Predicate MUST be `/bff/v1/**` (never `/bff/**`). JWT is AUTO-enforced (gateway ends with
   `anyRequest().authenticated()`) — do NOT add `/bff/**` to any permitAll allowlist. CORS reuses the
   existing global `gateway.cors.allowed-origins`; the dev profile (`application-dev.yml`) ALREADY
   allowlists `http://localhost:3000` — no CORS code change needed. Keep `internal-deny-route` first.
   Cleanup: verify `gateway.cors.*` (not the stray base `spring.cors.*`) binds across dev/staging/prod.
2. Service-catalog gap — APPROVED. Add web-bff to `docs/architecture/service-catalog.md` as a
   "Channel / BFF Service" (NOT a bounded context): port 9020, Simple Service Layer (ADR-004),
   Infrastructure Profile `none (stateless)` (ADR-006, same as api-gateway), starters-only (ADR-018),
   data ownership = none (owns no aggregates). Add to Section 3 (mode summary, alongside
   notification-service), Section 4 (data ownership), Section 5 (infra profile).
3. Thin-slice boundary — APPROVED. Browser -> gateway `/api/v1/**` (e.g. `POST /api/v1/payments` in
   16.4.2) SATISFIES "no browser-to-domain-service calls"; QA must NOT flag it. Boundary: allowed =
   gateway origin only (`/bff/v1/**` or `/api/v1/**`) with a validated Keycloak bearer; PROHIBITED =
   direct domain host/port or any browser call to `/internal/**`. Payment POST MUST carry an
   `Idempotency-Key` header.

## Execution order (waves; respects each feature file's Dependencies)

### Wave 0 — parallel, no cross-deps  [DONE 2026-07-12, verified]
- [x] 16.1.1 web-bff HTTP client + config bootstrap (M) — domain-engineer  [DONE, verified]
      Added `BearerTokenRelayInterceptor` (relays inbound Authorization bearer onto outbound calls),
      `GatewayClientConfig` (RestClient bean, base URL from `telco.gateway.base-url` in config-server,
      never hardcoded), `GatewayClient` (reusable transport for 16.4/16.5), `WebBffSecurityConfig`
      (permits /actuator/health + docs, authenticates the rest — sets up 16.1.2 JWT). Added
      `spring-boot-starter-security` to the pom (starter-security marks it optional). Added
      `telco.gateway.base-url` to configs/web-bff/application.yml. Fixed README drift (sprint path +
      "owns PKCE" -> "relays token"). Tests: GatewayClientTokenRelayTest (2, proves token relay + no-
      token pass-through) + WebBffApplicationTests (2, context loads + /actuator/health UP). `mvn -f
      microservices/web-bff/pom.xml verify` = BUILD SUCCESS, 4/4 green, JaCoCo check met. AC #1
      "registers with discovery" is a live-stack concern (deferred, Sprint 15 precedent). Two Boot-4
      gotchas hit + fixed: HttpHeaders.containsKey -> containsHeader; test needs
      spring.cloud.compatibility-verifier.enabled=false (normally from config-server).
- [x] 16.2.1 SvelteKit app scaffold, `frontend/web/`, `npm run build`/`dev` (M) — domain-engineer  [DONE, verified]
      Node 22.16.0 installed (not 20; Node 22 is also LTS) — using it for local dev, `engines.node`
      pinned `>=20` so CI can target Node 20 per decision. Fixed the failed agent's partial scaffold
      (vite.config.ts imported `sveltekit` from the wrong package -> `@sveltejs/kit/vite`). Added
      `.gitignore`. VERIFIED: `npm install` OK (194 pkgs), `npm run build` OK (adapter-node deployable
      build), `npm run dev` serves the shell on port 3000 (strictPort) -> HTTP 200, title "Telco CRM".

### Wave 1  [DONE 2026-07-12, verified]
- [x] 16.1.2 `/bff/v1` stub controllers + UI DTOs + Springdoc (M) — domain-engineer  [DONE, verified]
      BffController with 5 endpoints (home, onboarding/catalog, onboarding/order, account, invoices)
      returning UI DTOs (not ApiResult), all JWT-required; springdoc starter added (BOM-managed);
      /v3/api-docs covers all 5. Idempotency-Key: declared required=false + validated -> 400 (not the
      platform catch-all 500). `mvn verify` BUILD SUCCESS, 13 tests, JaCoCo 98.2% (gate 70%).
      Independently re-verified at wave close: 13/13 green, coverage met.
- [x] 16.2.2 routing shells + single typed BFF API client (`src/lib/api/client.ts`) (M) — domain-engineer  [DONE, verified]
      /login /onboarding /account /invoices placeholder shells under the shared layout; single typed
      ApiClient (only URL-constructing module, grep-confirmed no stray fetch), targets /bff/v1/** with a
      PUBLIC_BFF_BASE_URL env + gateway-origin dev default, injectable getAccessToken seam for 16.3.
      Added eslint flat config + prettier + vitest (9 tests). check/lint/test/build all green.
- [x] 16.2.3 path-filtered Node CI stage (lint + type-check + build) (S) — devops  [DONE, verified]
      New dedicated `.github/workflows/frontend-web-ci.yml` (native workflow-level `paths` filter on
      frontend/web/**), Node 20 + npm cache, `npm ci` -> lint/check/test/build, fail-on-error. actionlint
      v1.7.7 clean; path filter traced both directions (frontend change runs; backend change does not).

### Wave 2 — gateway + auth converge
- [x] 16.1.3 gateway route + CORS for `/bff/v1/**` (S) — devops  [DONE, applied per tech-lead ruling]
      Added `web-bff-route` (`uri: lb://web-bff`, `Path=/bff/v1/**`, no filters) to configs/api-gateway/
      application.yml; internal-deny stays first; JWT auto-enforced (no permitAll entry). Dev CORS origin
      localhost:3000 already allowlisted. BONUS REAL FIX: staging/prod CORS used the wrong key
      `telco.cors.*` (never read by the bean) -> corrected to `gateway.cors.*` so the existing
      staging/prod origins actually bind; removed the dead `spring.cors.*` block. Added the /bff/v1/**
      row to docs/api-contracts/api-gateway.md. Live E2E (curl through gateway + OPTIONS preflight)
      DEFERRED-TO-STACK (no stack up; Sprint 15 precedent); correctness proven statically. Tech-lead
      pre-approved the approach; final code-review covers it at sprint close. Feature 16.1 now DONE.
- [x] 16.3.1 Keycloak Auth-Code + PKCE login/logout/silent-renew via oidc-client-ts (L) — security  [DONE, verified offline]
      oidc-client-ts v3.5.0 against the `telco-web` public client (already in the realm export, redirect
      http://localhost:3000/*): login()/completeLogin()/logout()/silent-renew, tokens in sessionStorage
      (never localStorage), mirrored into an in-memory token-store fed to the client's getAccessToken
      seam (no second HTTP path). New /auth/callback route. Browser authority is localhost:8085 (Keycloak
      host origin), distinct from the 8080 server-side relay. check/lint/build green, 17 unit tests.
      LIVE login DEFERRED-TO-STACK (no Keycloak up). FLAGGED for realm owner (not applied): telco-web
      lacks `pkce.code.challenge.method=S256`, so PKCE is not server-ENFORCED (flow still works). Route
      guards (16.3.2) + E2E bearer proof (16.3.3) are Wave 3.

### Wave 3
- [x] 16.3.2 route guards + return-to-original-route (M) — security  [DONE, verified offline]
      `(protected)` route group (account/onboarding/invoices moved in, URLs unchanged) with a browser-
      guarded +layout.ts load + `ssr=false` (no unauthenticated content flash). route-guard.ts:
      resolveGuard/safeReturnTo/loginUrlFor; return-to carried via the OIDC `state` (login(returnTo) ->
      callback goto). safeReturnTo blocks open redirects + /login//auth loops. 28 tests (11 new),
      check/lint/build green. Live click-through DEFERRED-TO-STACK (no Keycloak up).
- [x] 16.3.3 bearer propagation browser -> BFF -> gateway, X-User-Id/Roles verified E2E (M) — security + observability  [DONE, verified offline]
      Subagent hit the session limit mid-task (wrote the client.ts seams); COMPLETED DIRECTLY in the
      main thread (user-approved) building on those seams. client.ts request() now: on 401 -> one silent
      renewAccessToken() + retry once; if still 401 (or renewal failed) -> onAuthRedirect() to /login
      (return-to preserved, 16.3.2) and reject — never a raw 401; non-401 errors untouched; retry carries
      the freshly renewed bearer. oidc.ts initAuth registers the real signinSilent renew handler +
      goto(loginUrlFor(current)) redirect handler (browser-guarded). 5 new client-401 tests; frontend
      total 33 tests; check/lint/build green. DEFERRED-TO-STACK: the live trace-level proof — when the
      stack runs, assert a logged-in GET /bff/v1/account reaches the gateway with the bearer and the
      downstream domain request carries X-User-Id == token subject and X-User-Roles == realm roles.
      Feature 16.3 now DONE.

### Wave 4 — feature composition (once auth chain closed)
- [x] 16.4.1 onboarding BFF composition (catalog in one call; order w/ Idempotency-Key) (L) — domain-engineer  [DONE, verified offline]
      BffController onboarding methods delegate to OnboardingCompositionService. catalog = GET
      /api/v1/tariffs + per-tariff /api/v1/addons merged in one response. order = register-or-reuse
      customer (POST /api/v1/customers + multipart KYC to /documents) -> resolve tariff -> POST
      /api/v1/orders forwarding the inbound Idempotency-Key downstream (order-service enforces it).
      GatewayClient extended with post/postMultipart + downstream-error translation (4xx->matching
      platform exception, 5xx/conn->503; no leaked 500). mvn verify BUILD SUCCESS, 20 tests, JaCoCo met.
      Live full-onboarding E2E DEFERRED-TO-STACK. Payment NOT composed (browser hits /api/v1/payments in 16.4.2).
- [x] 16.5.1 account/home/invoice BFF composition, strict X-User-Id scoping (L) — domain-engineer  [DONE, verified offline]
      AccountCompositionService behind home/account/invoices. home = customer + subscriptions(ACTIVE) +
      latest invoice in one call; account = + per-active-subscription usage/quota (used=total-remaining);
      invoices = paged, pdfUrl -> gateway route /api/v1/invoices/{id}/pdf (billing streams bytes, not
      pre-signed; browser downloads with its own token, web-bff never proxies). SELF-SCOPING: identity
      only from CurrentUserProvider.customerId(); endpoints bind no id param so a client-supplied
      ?customerId=<attacker> is ignored (test-proven); unlinked identity -> 403. mvn verify BUILD SUCCESS,
      25 tests, JaCoCo 93.4%. Independently re-verified at wave close. TWO in-scope build fixes: reinstalled
      stale .m2 platform jars (Jun 25, predated Sprint-14 UserContext.customerId()); added
      logstash-logback-encoder + loki-logback-appender to web-bff pom (platform logback needs them;
      web-bff's non-domain parent doesn't supply them — real latent runtime gap). Live E2E + real PDF
      download DEFERRED-TO-STACK.

### Wave 5 — UI  (serialized — all share client.ts / layout)
- [x] 16.4.2 onboarding wizard UI (register/KYC/order/pay, status polling) (L) — domain-engineer  [DONE, verified offline]
      6-step wizard in /onboarding: register -> kyc -> catalog -> review -> payment -> result. Framework-
      agnostic $lib/onboarding (wizard step machine, order-status poll loop, money/file utils) + 6 step
      components. Client extended with getOrderStatus (GET /api/v1/orders/{id}) + submitPayment
      (POST /api/v1/payments w/ Idempotency-Key) — thin-slice gateway calls, documented in the header.
      Final step renders ONLY the polled outcome (activated/failed/honest-timeout), never fake success.
      check/lint/build green, 56 tests (+23). 16.4.3 (rich failure/retry UX) intentionally left for Wave 6.
      Live click-through DEFERRED-TO-STACK.
- [x] 16.5.2 `/account` + `/invoices` pages incl. real PDF download (M) — domain-engineer  [DONE, verified offline]
      /account renders profile + per-subscription UsageGauge (data/voice/SMS) from getAccount(); /invoices
      renders a paged table with a real "Download PDF" per row. PDF download = authenticated client fetch
      (downloadInvoicePdf -> GET /api/v1/invoices/{id}/pdf with bearer + Accept:application/pdf) -> Blob ->
      browser save (a plain <a href> would omit the bearer -> 401). Client's 401 logic refactored into a
      shared dispatch() reused by request() (JSON) + requestBlob(). FIXED stale client.ts types to match the
      real 16.5.1 BFF DTOs (per-subscription usage, paged invoices). check/lint/build green, 74 tests (+18).
      Live render + real PDF byte-stream DEFERRED-TO-STACK.
- [x] 16.5.3 post-login dashboard from single `GET /bff/v1/home` (S) — domain-engineer  [DONE, verified offline]
      Dashboard on the public home route `/`, driven by the authState store: unknown -> "starting up";
      anonymous -> welcome + Sign in (NO getHome call, so no 401); authenticated -> single api.getHome()
      -> DashboardSummary (profile + active subscriptions + latest invoice w/ paid/overdue tone badge) with
      loading/error(retry)/empty states, links to /account + /invoices. Framework-agnostic $lib/home/
      summary.ts (8 tests). HomeDashboard type verified vs the real BFF DTOs — no drift, client.ts untouched.
      SSR-safe (getHome/login off the SSR path via a guarded $effect). check/lint/build green, 82 tests (+8).
      Feature 16.5 now DONE. Consolidated Wave-5 frontend sign-off: 82 tests + build green.

### Wave 6 — resilience UX + exit gate
- [x] 16.4.3 payment-failure / KYC-rejection UX (no stack traces, corrective routing) (M) — domain-engineer  [DONE, verified offline]
      New framework-agnostic $lib/onboarding/recovery.ts (recoveryActionFor status -> retry-payment|
      restart-kyc|none; buildPaymentAttempt mints a fresh Idempotency-Key per attempt). ResultStep failed
      branch split into two honest, actionable states: payment failure -> "cancelled & refunded" message +
      Retry payment (fresh key, no replay) / Start over (keeps details+plan); KYC rejection
      (REJECTED/KYC_REJECTED/KYC_FAILED now terminal) -> route back to the kyc corrective step, preserving
      input. check/lint/build green, 90 tests (+8). Feature 16.4 now DONE -> Sprint 16 is 5/5 features.
      Live saga round-trip DEFERRED-TO-STACK.
- [x] Exit gate: full-path acceptance (login -> onboard -> account/usage -> PDF) with no
      browser-to-domain-service call — qa. Offline gate PASSED (see below), then the LIVE full path was run
      for real on 2026-07-13 and PASSED (see OUTCOME at the top). Note the flow no longer contains a "pay"
      step: the browser payment POST was a defect (ADMIN-only endpoint; charges are event-driven).

## Offline exit-gate (2026-07-13) — historical. Superseded by the live E2E above (see OUTCOME)
> Kept for the record. This gate passed with EVERY suite green while the product was NON-FUNCTIONAL in a
> browser — that is precisely why an offline gate is not a substitute for a live run.
All 5 features built + offline-verified: web-bff `mvn verify` 25 tests/JaCoCo 93.4%; frontend 90 vitest +
check/lint/build green; `frontend-web-ci.yml` actionlint-clean.
- qa gate: **PASS** — suites green, the no-browser-to-domain-service invariant HOLDS (client.ts is the only
  fetch, targets only /bff/v1 + /api/v1 on one gateway base; sole non-gateway origin is Keycloak :8085 for
  PKCE), the 4 key behaviors genuinely asserted (self-scoping w/ distinct attacker UUID + 403; full 401
  renew/retry/redirect; polled-outcome-only onboarding; authenticated Blob PDF), coverage loophole CLOSED,
  deferred ledger honest.
- code-review: **CHANGES-REQUIRED -> all addressed.** (1) MEDIUM: gateway CORS allowedHeaders omitted
  `Idempotency-Key` -> the cross-origin (localhost:3000->8080) preflight would block the onboarding-order +
  payment POSTs (headline flow). FIXED in GatewaySecurityConfig.java (added the header; api-gateway compile
  BUILD SUCCESS). (2) LOW/advisory: onboarding reuse path trusts a client `customerId` (by design, order-
  service re-checks) -> documented in docs/api-contracts/web-bff.md Notes. All ADR/ARC checks otherwise
  APPROVE. qa's stale pom-comment nit (web-bff "no tests yet") also corrected.
At the time, the sprint EXIT CRITERIA (live PKCE login -> onboarding saga -> account/usage view -> real PDF
download) were recorded as DEFERRED-TO-STACK and Sprint 16 as DONE(features). **That posture is now closed:**
the criteria were discharged for real on 2026-07-13 against the live local Compose stack (see OUTCOME at the
top of this file). Sprint 16 = DONE. Sprint 15's own deployed-environment (Kind) tail is unaffected and
remains open.

Critical path: 16.1.1 -> 16.1.2 -> 16.3.3 -> {16.4.1, 16.5.1} -> {16.4.2, 16.5.2} -> 16.4.3.

## Cross-cutting DoD (per CLAUDE.md + STATUS.md notes)

- web-bff currently has ZERO tests; JaCoCo no-ops. Do NOT inherit that loophole — qa sets a real
  coverage bar for new web-bff code as part of DoD.
- Fix web-bff README drift during 16.1: stale sprint path (`sprint-16-frontend-and-bff` ->
  `sprint-16-web-frontend`) and the wrong "BFF owns PKCE" wording (the BFF only RELAYS the token).
- No emojis anywhere (ARC-09). External REST under documented prefixes; no business logic in
  controllers; services depend only on starters (ADR-018).

## Review checkpoints

- After each feature reaching DONE: verify acceptance criteria against a real run (web-bff up +
  gateway + Keycloak via compose; frontend build/dev), not just "files written"; report before proceeding.
- Update sprint-16 README (Features table + header) and STATUS.md together at each DONE.
- Capture any correction as a lesson in `docs/tasks/lessons.md`.
- code-review (ADR-compliance) before the sprint is called complete.

## Status-tracking updates needed at kickoff (apply together)

- `docs/tasks/sprint-16-web-frontend/README.md` header: TODO 0/5 -> IN PROGRESS.
- `docs/tasks/STATUS.md` Sprint Rollup row 16: TODO 0/5 -> IN PROGRESS; top-of-file narrative + totals.
- On 16.3 completion: tick `docs/architecture/keycloak-and-auth.md` Section 9 web-client PKCE item.
- Add the missing web-bff row to `docs/architecture/service-catalog.md` (via architecture/tech-lead).
