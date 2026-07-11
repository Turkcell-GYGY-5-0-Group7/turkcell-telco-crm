# Sprint 19 - Service Mesh and mTLS (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). It is documented now
> and built later (ADR-026). Feature subtask files will be authored when the sprint is scheduled.

## Objective

Enforce mutual TLS for all internal service-to-service traffic per ADR-026, closing the residual risk
`docs/architecture/security-posture.md` Section 8 explicitly accepted for the MVP (an in-cluster
attacker forging `X-User-Id`/`X-User-Roles` headers to bypass the gateway). Adopts **Linkerd** with
automatic sidecar injection as the mesh, plus **default-deny Kubernetes NetworkPolicies** as an
in-scope companion control. The existing gateway-behind-trust *user*-identity model (Keycloak JWT,
gateway-validated, `X-User-Id`/`X-User-Roles` injection, `@PreAuthorize`/mediator `AuthorizationRule`)
is unchanged - this sprint adds a second, orthogonal workload-identity trust layer on top of it, per
ADR-026 Section 2.

## Sequencing note

Per ADR-026 Section 4, this sprint has **no hard technical dependency** on Sprint 18 (Vault):
Linkerd's Identity component self-issues and auto-rotates its own workload mTLS certificates and does
not require an external PKI or secret store to deliver this sprint's mTLS guarantee. This sprint is
nonetheless **sequenced after Sprint 18** for operational reasons only - stated explicitly rather than
assumed: standing up one new in-cluster security control plane (Vault) and validating that pattern on
this specific Kind/Helm stack once, before repeating the same shape of work for a second control plane
(Linkerd), is lower-risk than doing both in parallel with the same team. Nothing in this sprint's
scope requires Vault-issued mesh certificates; a future hardening pass MAY point Linkerd's trust anchor
at Vault's PKI secrets engine, but that is optional and out of scope here.

## Included Epics

- Epic 19: Zero-Trust Networking (service mesh mTLS + default-deny NetworkPolicies)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 19.1 | Linkerd control-plane install (`deploy/helm/` release) + namespace injection annotation | TODO | [19.1-linkerd-control-plane-install-and-namespace-injection.md](19.1-linkerd-control-plane-install-and-namespace-injection.md) |
| 19.2 | Verify automatic sidecar injection and mTLS across all 13 services; re-verify HPA/PDB resource accounting with the sidecar's footprint | TODO | [19.2-verify-sidecar-injection-mtls-and-hpa-pdb-accounting.md](19.2-verify-sidecar-injection-mtls-and-hpa-pdb-accounting.md) |
| 19.3 | Per-service `Server`/`AuthorizationPolicy`: only the gateway's mesh identity may call downstream services; `/internal/**` remains edge-denied | TODO | [19.3-per-service-server-authorizationpolicy-gateway-only.md](19.3-per-service-server-authorizationpolicy-gateway-only.md) |
| 19.4 | Default-deny `NetworkPolicy` per namespace + explicit allow rules matching the actual service-catalog call graph | TODO | [19.4-default-deny-networkpolicy-and-explicit-allow-rules.md](19.4-default-deny-networkpolicy-and-explicit-allow-rules.md) |
| 19.5 | Live verification on the Sprint 15 Kind cluster: forged-header bypass attempt fails; legitimate gateway-to-service and Kafka/Postgres/Redis traffic is unaffected | TODO | [19.5-live-verification-forged-header-bypass-and-smoke-test.md](19.5-live-verification-forged-header-bypass-and-smoke-test.md) |

## Sprint Deliverables

- Linkerd installed as another `deploy/helm/` release, alongside `dependencies`, `telco-service`, and
  Vault (Sprint 18), following the platform's established one-chart-per-concern pattern.
- All 13 services running with an injected `linkerd2-proxy` sidecar, mTLS active for all meshed
  in-cluster traffic by default.
- `Server`/`AuthorizationPolicy` resources restricting each downstream service to accept calls only
  from the gateway's verified mesh workload identity, closing the specific gap
  `security-posture.md` Section 8 named.
- Default-deny `NetworkPolicy` objects in the `telco` namespace, with explicit allow rules for every
  real edge in the service-catalog's call graph (gateway -> domain services, domain services -> their
  own Postgres/Redis/Kafka/MinIO/Keycloak dependencies) - the compensating control for pods that are
  not yet meshed, per ADR-026 Section 3.
- `deploy/RUNBOOK.md` and `deploy/helm/README.md` updated with the mesh install step and a mesh
  verification command, matching the operational-completeness bar Sprint 15 set.

## Exit Criteria

- ADR-026 is ratified (Accepted) by tech-lead before any code in this sprint ships (the ADR is
  Proposed as of this drafting).
- Live on the Sprint 15 Kind cluster: a pod attempting to call a downstream domain service directly
  with a forged `X-User-Id`/`X-User-Roles` header, from outside the gateway's mesh identity, is
  rejected at the mesh policy layer - the exact residual risk `security-posture.md` Section 8 accepted
  is demonstrably closed, not just asserted.
- HPA (`min 2 / max 5 / 75% CPU`) and PDB (`minAvailable: 1`) behavior, live-verified in Sprint 15.3,
  is re-verified with the sidecar's added resource footprint and pod count accounted for.
- The full smoke test (`deploy/smoke/smoke-test.sh`, Sprint 15.4.3) passes unchanged with the mesh and
  NetworkPolicies in place - proving legitimate traffic (gateway -> services, services -> Postgres/
  Redis/Kafka/MinIO/Keycloak) is unaffected.
- No change to any service's JWT validation, `@PreAuthorize` rule, or mediator `AuthorizationRule` -
  the user-identity trust layer (ADR-011) is verified unchanged, per ADR-026 Section 2.

## References

- [ADR-026 Service Mesh and mTLS](../../../architecture/adr/ADR-026-service-mesh-and-mtls.md)
- [docs/architecture/security-posture.md](../../architecture/security-posture.md) Section 8 (mTLS
  decision) - the current-state MVP deferral record this sprint's ADR supersedes, and Section 10
  (hardening checklist) - the line item this sprint closes.
- [architecture/adr/ADR-011-security-foundation.md](../../../architecture/adr/ADR-011-security-foundation.md) -
  the user-identity/JWT/gateway model this sprint layers onto without changing.
- [docs/product/TELCO-CRM-ADVANCED.md](../../product/TELCO-CRM-ADVANCED.md) Section 4.1 (Zero-Trust and
  Service Mesh) - the forward-looking design this sprint delivers the mesh/mTLS half of (OPA
  policy-as-code remains future work).
- [deploy/helm/README.md](../../../deploy/helm/README.md) and
  [deploy/RUNBOOK.md](../../../deploy/RUNBOOK.md) - the chart layout, install order, and live
  Kind-verification standard this sprint follows.
- [Sprint 15 - Deployment](../sprint-15-deployment/README.md) - the HPA/PDB, Ingress, and smoke-test
  baseline this sprint must not regress.
- [Sprint 18 - Secret Management](../sprint-18-secret-management/README.md) - sequenced before this
  sprint for operational reasons (ADR-026 Section 4); not a hard technical dependency.
- [docs/architecture/service-catalog.md](../../architecture/service-catalog.md) - the service call
  graph Feature 19.4's NetworkPolicy allow rules are derived from.
