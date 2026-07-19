# Working TODO — Full E2E Re-Test (Sprint 14-style) covering post-Sprint-14 features

Goal: re-prove the platform end to end on a fresh live Docker Compose stack, now including the
post-MVP features that shipped without acceptance coverage (campaign-service + order discount
integration from Sprint 21, web-bff from Sprint 16), plus a browser E2E of the frontend and the k6
NFR-01 perf re-run. Also live-verifies the unmerged order-service RestClient-timeout commit
(`c3ee8a1`). Plan approved 2026-07-18; full plan at
`~/.claude/plans/i-want-to-test-shimmying-hippo.md`. Results land in
`docs/tasks/sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md`.

Thermal constraint: all code/doc work with the stack down; builds never concurrent with a live
stack; one contiguous stack-up window for all live phases, `make infra-down` immediately after;
long live commands wrapped in `caffeinate -dims`.

## Phase 0 — Preconditions
- [x] Docker Desktop running; VM gate passed (31.3 GiB / 16 CPUs, >= 12 GiB required)
- [x] Plan mirrored here

## Phase 1 — Wire campaign-service into compose (stack down)
- [x] `infra/docker/compose.yml`: campaign-service block (app-common anchor, port 9011, healthcheck);
      MEMORY BUDGET comment updated
- [x] `infra/docker/.env` + `.env.example`: `CAMPAIGN_SERVICE_PORT=9011`;
      `PSP_MOCK_FORCE_OUTCOME=SUCCESS` in `.env`
- [x] Gate: `docker compose --profile apps config` renders the merged block

## Phase 2 — New acceptance ITs (stack down)
- [x] `support/CampaignAdminApi.java` (direct :9011; javadoc cites Feature 21.1.3 no-gateway-route
      ruling), `support/CampaignDb.java` (JDBC redemption read)
- [x] `AcceptanceConfig` constants + `GatewayApi.createOrder` campaignCode overload +
      acceptance-tests pom postgresql test dep
- [x] `campaign/CampaignDiscountedOrderAcceptanceIT.java` (discounted unitPrice, campaignId,
      redemption RESERVED->CONFIRMED)
- [x] `bff/WebBffSmokeAcceptanceIT.java` (home/catalog/account/invoices 200 + 401 unauthenticated)
- [x] `campaign/CampaignFailOpenAcceptanceIT.java` (env-gated `CAMPAIGN_FAILOPEN_ENABLED`,
      docker stop/start, full price + null campaignId)
- [x] Gate: `mvn -pl acceptance-tests -am -Pacceptance verify -DskipITs` compiles clean

## Phase 3 — Build then boot (single stack-up window opens)
- [x] `make infra-destroy` (fresh volumes: campaign_db initdb, realm import, MSISDN pool reset)
- [x] `make infra-platform-build && make infra-apps-build` (stack down; cool-down after)
- [x] `make infra-up-full-stack`; health gate: :8080, :9001-:9011, :9020, Keycloak well-known
- [x] `make infra-connectors`; gate: 11 connectors RUNNING incl. campaign

## Phase 4 — Backend suites
- [x] Main sweep green: AC-01 (+compensation), AC-02, AC-03, discounted-order, web-bff smoke
- [x] Fail-open IT green (separate invocation, campaign-service restarted healthy after)

## Phase 5 — Browser E2E (stack stays up)
- [x] `frontend/web` dev server; PKCE login -> register + KYC -> order + pay -> FULFILLED ->
      account/usage -> invoice PDF; screenshots at each gate
- [x] Known Sprint 16 open defects noted if hit (addons 500, duplicate-TCKN 500, multipart 500) —
      not fixed here

## Phase 6 — k6 perf re-run (last; then window closes)
- [x] Cool-down; `caffeinate -dims k6 run microservices/acceptance-tests/perf/api-latency-load-test.js`;
      p95(expected_response) < 300ms; compare vs 14.3.1 report
- [x] Evidence captured (logs, compose ps, DB reads); `make infra-down` immediately

## Phase 7 — Documentation (stack down)
- [x] `docs/tasks/sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md` run report
- [x] Status touches: sprint-14 / sprint-16 / sprint-21 READMEs, dated STATUS.md entry, this file
      checked off
