# Sprint 16 - Web Frontend (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE (features); live E2E exit criteria deferred-to-stack | 5/5 | 2026-07-13 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). It is documented now
> and built later (ADR-022). Feature subtask files will be authored when the sprint is scheduled.

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

- A user logs in via Keycloak (PKCE), completes onboarding through the UI, and views their
  subscription, usage, and a downloadable invoice PDF.
- No browser-to-domain-service calls; all traffic flows through the BFF/gateway with a validated
  Keycloak token.

## References

- [ADR-022 Frontend and BFF Strategy](../../../architecture/adr/ADR-022-frontend-and-bff-strategy.md)
- [web-bff contract](../../api-contracts/web-bff.md)
- [keycloak-and-auth.md](../../architecture/keycloak-and-auth.md)
