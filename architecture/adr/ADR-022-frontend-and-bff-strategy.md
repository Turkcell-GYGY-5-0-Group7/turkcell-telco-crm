# ADR-022 Frontend and BFF Strategy

Status: Accepted
Date: 2026-06-23

## Context

The MVP brief scoped out a UI (backend + Swagger only). However, ADR-011 Section 5 already designs a
Backend-for-Frontend (BFF) layer, and the Keycloak realm ships a `telco-web` public client
(Authorization Code + PKCE, redirect `http://localhost:3000/*`). We now want a simple web frontend to
demonstrate the platform end to end (login, onboarding, account, invoices).

We need a decision on the frontend framework, the channel-facing composition layer, the auth flow,
and where this lives in the repo. This ADR documents the strategy; implementation is a later sprint.

## Decision

### Framework

The web application is built with **SvelteKit (Svelte 5) + TypeScript + Vite**. Rationale: smallest
footprint for a focused demo UI, fast iteration, first-class SSR/routing, and good OIDC support via
`oidc-client-ts`.

### Backend-for-Frontend (BFF)

A **web-bff** service (ADR-011 Section 5) sits between the SvelteKit app and the domain services:

* composes multiple domain API calls into UI-shaped responses,
* relays the user's token to the gateway,
* enforces a channel security boundary (no direct browser-to-domain-service calls).

For the first thin slice the SvelteKit app MAY call the API gateway directly; the web-bff is
introduced as the composition needs grow. Either way, the browser never calls a domain service
directly (ADR-011 Section 2).

### Authentication

The browser authenticates with **Keycloak via Authorization Code + PKCE** using the `telco-web`
client. Tokens are validated by the gateway (JWKS). The frontend never sees a client secret. See
[`docs/architecture/keycloak-and-auth.md`](../../docs/architecture/keycloak-and-auth.md).

### Repository layout

The frontend lives at the repository root under `frontend/web/` (separate from the JVM
`microservices/` reactor). The web-bff, being a JVM service, follows the ADR-017 service template and
lives under `microservices/web-bff/` (or as a Node BFF under `frontend/` if a later ADR amends this).

### API standards

The BFF exposes `/bff/v1/**`; it is not a domain service and does not own data. Domain contracts
remain under `docs/api-contracts/`; the BFF contract is [`web-bff.md`](../../docs/api-contracts/web-bff.md).

## Consequences

### Positive

* End-to-end demonstrable platform (real login + flows).
* Clean channel boundary; domain services stay UI-agnostic.
* Reuses the existing Keycloak `telco-web` client and roles.

### Negative

* A new language/runtime (Node/SvelteKit) in the repo and CI.
* An extra network hop (BFF) and its own deployment.

## Alternatives Considered

* **React + Vite** - largest ecosystem; rejected for this project in favor of SvelteKit's lighter
  footprint for a demo UI (team preference).
* **Angular** - first-class Keycloak adapter but heavier; rejected for scope.
* **No BFF (browser -> gateway only)** - acceptable for the thin slice but does not satisfy ADR-011's
  channel-composition model as the UI grows; BFF remains the target.

## Related ADRs

* ADR-011 Security Foundation (BFF layer, PKCE, gateway validation)
* ADR-015 API Design Standards
* ADR-010 Service Discovery and Configuration
