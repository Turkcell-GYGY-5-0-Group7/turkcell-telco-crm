# Sprint 16 - Web Frontend (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE - exit criteria MET (live E2E run 2026-07-13; re-proven 2026-07-18) | 5/5 | 2026-07-18 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). Built per ADR-022.

## Objective

Deliver a simple web frontend that demonstrates the platform end to end: Keycloak login (Authorization
Code + PKCE), customer onboarding (register -> KYC -> order -> pay), and account views (subscriptions,
usage, invoices). Introduce a Web BFF to compose domain APIs for the UI. Built per ADR-022
(SvelteKit + Svelte 5 + TypeScript) and ADR-011 (BFF, gateway-validated Keycloak tokens).

## Included Epics

- Epic 16: Web Channel (web frontend + web-bff)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 16.1 | Web BFF scaffold and gateway integration | DONE | [16.1-web-bff-scaffold-and-gateway-integration.md](16.1-web-bff-scaffold-and-gateway-integration.md) |
| 16.2 | SvelteKit app scaffold and routing | DONE | [16.2-sveltekit-app-scaffold-and-routing.md](16.2-sveltekit-app-scaffold-and-routing.md) |
| 16.3 | Keycloak Authorization Code + PKCE login | DONE | [16.3-keycloak-authorization-code-pkce-login.md](16.3-keycloak-authorization-code-pkce-login.md) |
| 16.4 | Onboarding wizard (register, KYC, order, pay) | DONE | [16.4-onboarding-wizard.md](16.4-onboarding-wizard.md) |
| 16.5 | Account views (subscriptions, usage, invoices) | DONE | [16.5-account-views.md](16.5-account-views.md) |

## Sprint Deliverables

- `frontend/web/` SvelteKit app with login and the onboarding + account flows.
- `web-bff` service composing domain APIs (`/bff/v1/**`), relaying Keycloak tokens to the gateway.
- The browser never calls a domain service directly (ADR-011).

## Exit Criteria

- [x] A user logs in via Keycloak (PKCE), completes onboarding through the UI, and views their
  subscription, usage, and a downloadable invoice PDF. **MET** - live, human-driven browser run on
  2026-07-13 (see below).
- [x] No browser-to-domain-service calls; all traffic flows through the BFF/gateway with a validated
  Keycloak token. **MET** - `client.ts` is the only fetch seam; it targets only `/bff/v1/**` and
  `/api/v1/**` on the single gateway origin (the one non-gateway origin is Keycloak :8085 for PKCE).

### Live end-to-end run (2026-07-13) - the exit criterion, discharged

Run by a human in a real browser against the live local Docker Compose stack (18-container subset;
"live" = real running services on localhost, not a deployed cluster):

Keycloak PKCE login -> onboarding wizard (register -> KYC upload -> tariff -> review -> place order)
-> the real saga (order FULFILLED, subscription ACTIVE, MSISDN 905320000006 assigned) -> dashboard ->
`/account` showing usage/quota (0/20480 MB, 0/1000 min, 0/500 SMS) -> bill-run -> `/invoices` ->
invoice PDF downloaded. Self-scoping verified on real data: the bill-run issued 7 invoices and the
user's `/invoices` returned exactly 1 - their own.

Supporting suites, all green on the same day: frontend 153 tests + svelte-check 0 errors + lint/build
clean; web-bff 31 tests (JaCoCo pass); customer-service 95 tests (JaCoCo pass); api-gateway 9 tests
(JaCoCo pass).

**The live run found 11 real defects that every offline suite had missed**, several of which made the
shipped web channel completely non-functional in a browser (the already-pushed commit `d8422f5`
contains that broken code). 9 were fixed as part of closing this sprint; see
[../STATUS.md](../STATUS.md) (top entry) for the full list and the root cause (each layer was tested
against its own mock, so a contract mismatch *between* two independently-mocked layers was invisible;
three defects were reachable only by a human in a browser).

### Tracked follow-ups (open; NOT done, NOT blocking Sprint 16)

| # | Item | Owner |
| --- | --- | --- |
| 1 | Duplicate TCKN registration returns 500 instead of a clean 409 Conflict (customer-service). | domain-engineer |
| 2 | ANY oversized multipart returns 500, not 413: `starter-api`'s `GlobalExceptionHandler` catches `Exception` before Spring's own 413 mapping. Platform-starter issue. | platform-engineer / tech-lead |
| 3 | `POST /api/v1/addons` is documented in `docs/api-contracts/product-catalog-service.md` but NOT implemented (`AddonController` has only a GET); it returns 500. Pre-existing Sprint 07 gap. Addons are optional in the wizard so it did not block the E2E - but **the addon selection path is therefore UNPROVEN end-to-end**. | domain-engineer |
| 4 | Customer status remains PENDING after onboarding (KYC approval is a separate admin step). Expected behaviour, recorded for clarity - not a bug. | n/a |

## Re-verification (2026-07-18)

The full browser journey was re-proven end to end during the post-Sprint-21 E2E re-test (Feature
14.6): first-attempt PKCE login with a freshly provisioned linkable subscriber, register/KYC/plan/
order through the wizard, saga observed to FULFILLED in the UI, dashboard/account composed with real
MSISDN and quota, invoice self-scoping re-verified (1 of 8 visible), invoice PDF download 200. A new
permanent `WebBffSmokeAcceptanceIT` (5 tests) now guards the four `/bff/v1` GET composition
endpoints in the acceptance suite. Follow-ups 1-3 above remain open (off the happy path, unchanged).
Full detail:
[../sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md](../sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md).

## References

- [ADR-022 Frontend and BFF Strategy](../../../architecture/adr/ADR-022-frontend-and-bff-strategy.md)
- [web-bff contract](../../api-contracts/web-bff.md)
- [keycloak-and-auth.md](../../architecture/keycloak-and-auth.md)
