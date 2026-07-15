# ADR-026 Service Mesh and mTLS

Status: Accepted
Date: 2026-07-11

---

## Context

`docs/architecture/security-posture.md` Section 8 is a complete, reviewed decision record: internal
service-to-service traffic runs over plain HTTP under a **gateway-behind-trust** model for the MVP,
with an explicit deferral rationale, an accepted residual risk, and a production recommendation. It is
authoritative for the current state and this ADR must not silently restate or contradict it - it
supersedes it (see Supersession below).

Quoting the parts this ADR must engage with directly:

* **Residual risk accepted for the MVP**: "An attacker with in-cluster network position could call a
  downstream service directly with forged `X-User-Id` / `X-User-Roles` headers, bypassing the
  gateway... NetworkPolicies and namespace isolation are the compensating controls" - which, per
  Section 10's checklist, are **not yet implemented** (`[ ] mTLS for internal traffic (mesh or
  SPIFFE/SPIRE) + default-deny NetworkPolicies`).
* **Production recommendation**: "service mesh such as Istio/Linkerd, or SPIFFE/SPIRE-issued workload
  certificates... combine with Kubernetes NetworkPolicies (default-deny, explicit allow)... service-to-
  service auth in production is mTLS (SPIFFE/PKI), not bearer tokens."

`architecture/adr/ADR-011-security-foundation.md` already commits the platform, at a principle level,
to mTLS for internal traffic ("All internal communication MUST use Mutual TLS," Section 3) and to
"Services MUST trust mTLS identity internally" (Section 6) - but ADR-011 never selected an issuance
mechanism, and the platform's actual delivered posture (per `security-posture.md`, which is the
authoritative *current-state* record) is gateway-behind-trust over plain HTTP, not mTLS. This ADR is
the concrete mechanism-selection ADR that ADR-011 anticipated but left open, reconciled against what
actually shipped.

`docs/product/TELCO-CRM-ADVANCED.md` Section 4.1 (Zero-Trust and Service Mesh) frames the target end
state: "Replace gateway-behind-trust (MVP) with mTLS everywhere via a service mesh (Istio/Linkerd):
every service-to-service call is mutually authenticated and authorized (SPIFFE/SPIRE identities). ...
least-privilege network policies per namespace," and Section 9 lists "Service mesh and zero-trust
(mTLS, SPIFFE, OPA policy-as-code)" as a proposed ADR. This ADR is that ADR, scoped to what the
platform's actual current shape needs and can absorb: 13 domain+infra services (`docs/architecture/
service-catalog.md`), a single-cluster Kind-based deployment (Sprint 15,
`deploy/helm/README.md`/`deploy/RUNBOOK.md`), Helm-charted with one release per service. OPA
policy-as-code is not addressed here; it is a separate, larger authorization-model decision and is
left for a future ADR if the platform needs it.

---

## Decision

We adopt **Linkerd** as the service mesh, with **automatic sidecar injection providing mTLS for all
in-cluster service-to-service traffic**, and we adopt **default-deny Kubernetes NetworkPolicies with
explicit allow rules** as an in-scope companion control delivered in the same sprint. The existing
gateway-behind-trust *user*-identity model (JWT validation, `X-User-Id`/`X-User-Roles` injection) is
**unchanged** - the mesh adds a second, orthogonal trust layer, it does not replace the first.

### 1. Mesh technology: Linkerd, not Istio, not SPIFFE/SPIRE without a mesh

* **Rejecting SPIFFE/SPIRE without a mesh**: SPIFFE/SPIRE alone issues workload identities (SVIDs) but
  leaves each workload responsible for consuming them - terminating/presenting mTLS itself. For 13
  Spring Boot services that today speak plain HTTP via Spring Cloud Gateway `lb://` routes and plain
  `RestClient`/Feign-style calls, that means adding SPIFFE-aware TLS material loading and SVID rotation
  handling to every service's HTTP client and server configuration (or a per-pod `spiffe-helper`
  sidecar plus manual Spring `SSLContext` wiring) - meaningful application-level engineering work
  repeated 13 times for the same outcome a mesh gives transparently via sidecar injection with zero
  application code change. Given ADR-018's "services depend only on platform starters" discipline, an
  outcome that requires bespoke per-service TLS wiring is a worse fit than one that requires none.
* **Rejecting Istio**: Istio's control plane (Istiod) and CRD surface (`VirtualService`,
  `DestinationRule`, `PeerAuthentication`, `AuthorizationPolicy`, Gateway API resources, WASM plugins)
  is built for traffic-management needs this platform does not have today - canary/traffic-split
  routing, multi-cluster federation, edge-gateway replacement. This platform's actual shape (per
  `deploy/helm/README.md`) is a single Kind-based cluster, one Spring Cloud Gateway already doing edge
  routing, and Envoy-class sidecar resource overhead (typically the heaviest of the mesh proxies) added
  to *every* pod across HPA'd (2-5 replica) domain services is a real cost for capability the platform
  is not using. Istio remains the right call if the platform later needs multi-cluster or fine-grained
  traffic shaping; it is not justified by this ADR's actual requirement, which is "every internal call
  is mutually authenticated."
* **Choosing Linkerd**: Linkerd's proxy (`linkerd2-proxy`, Rust-based) is the lightest-weight of the
  mainstream options, mTLS is automatic and on by default for meshed pods with no policy authoring
  required for the base case (closing the residual risk with minimal new YAML), and its control plane
  (`linkerd-identity`, `linkerd-destination`, `linkerd-proxy-injector`) is small enough to run
  comfortably alongside the `dependencies` chart's already-substantial in-cluster footprint (postgres,
  redis, mongo, kafka, schema-registry, kafka-connect, minio, keycloak, otel-collector, tempo, loki,
  prometheus, grafana - Section 15.2.3). This matches the platform's actual scale (13 services, one
  cluster) better than Istio's heavier control plane, without the per-service engineering cost of
  SPIFFE/SPIRE alone.
* Adoption mechanics (installation via the `linkerd` Helm charts as another `deploy/helm/` release,
  namespace annotation for automatic sidecar injection, per-service `Server`/`AuthorizationPolicy`
  resources restricting inbound traffic to meshed+authenticated callers) are devops/platform-engineer
  implementation detail for Sprint 19, not decided further in this ADR.

### 2. Two distinct trust layers - the gateway-behind-trust model is not replaced

This is the precise point `security-posture.md` Section 8 and ADR-011 leave implicit and this ADR
makes explicit, because conflating them is the most likely implementation mistake:

* **Layer 1 - workload identity (new, this ADR).** Linkerd's Identity control-plane component issues
  each pod a short-lived, mesh-scoped mTLS certificate (rotated automatically, default ~24h validity)
  bound to its `ServiceAccount` identity. Every proxy-to-proxy connection is mutually authenticated at
  the transport layer. This proves *which workload* originated a call - specifically, it lets a
  downstream service's `Server`/`AuthorizationPolicy` say "only the pod running as the
  `api-gateway` `ServiceAccount`'s mesh identity may call me," which is exactly the compensating
  control `security-posture.md` Section 8 named as accepted-but-missing risk: an attacker with raw
  in-cluster network position can no longer call a downstream service directly with forged headers,
  because the downstream service's mesh policy rejects any caller that is not cryptographically the
  gateway's workload identity, independent of what HTTP headers the request carries.
* **Layer 2 - user identity (unchanged, ADR-011).** Keycloak remains the token issuer; the API Gateway
  remains the only component that validates the JWT and the only source of `X-User-Id`/
  `X-User-Roles`; `@PreAuthorize` and the mediator `AuthorizationRule` keep consuming those headers
  exactly as today. **mTLS does not carry user identity** - it authenticates the workload (the
  gateway pod), not the end user, so it cannot and does not replace JWT validation or header
  injection. A downstream service now has *two* independent guarantees instead of one: "this HTTP
  request carries headers the gateway vouches for" (Layer 2, unchanged) plus "this TCP connection is
  cryptographically provably from the gateway's workload, not a forged copy" (Layer 1, new). Neither
  layer alone was sufficient; `security-posture.md`'s own residual-risk paragraph is precisely the gap
  between them, and closing it is this ADR's purpose.
* Consequence for `starter-security`: no change. Services that additionally validate the JWT directly
  via `starter-security` for defense-in-depth (an existing option per `security-posture.md` Section 1)
  continue to be able to do so; the mesh is transparent to the HTTP payload and does not touch JWT
  validation logic.

### 3. NetworkPolicies: in scope, delivered alongside the mesh, not deferred

`security-posture.md` Section 8's production recommendation pairs mTLS with "default-deny
NetworkPolicies, explicit allow" as a single combined control, not two independently-schedulable
items. This ADR keeps that pairing: **default-deny NetworkPolicies (with explicit per-service allow
rules matching the service-catalog's actual call graph) are in this ADR's scope**, delivered in the
same sprint as the mesh rollout (Sprint 19, see the companion sprint README), for a concrete reason
specific to how a mesh degrades: Linkerd's per-`Server` authorization policies restrict what the
*meshed* data path allows, but a pod that is not yet injected (a rollout gap, a misconfigured
namespace annotation, a pod bypassing the proxy) can still reach another pod's application port
directly in plain HTTP unless the network layer itself also denies it. NetworkPolicy is the
compensating control for exactly that gap, and shipping mTLS without it would leave the same class of
residual risk `security-posture.md` already flagged, only partially closed. Splitting them into two
ADRs/sprints would recreate the gap this ADR exists to close.

### 4. Sequencing: this ADR does not have a hard technical dependency on ADR-025, but Sprint 19 is still sequenced after Sprint 18

The question this ADR must answer without assuming: does Linkerd need Vault-issued certificates?
**No.** Linkerd's Identity component is self-contained by design: it generates and manages its own
trust anchor (root CA) and issuer certificate, and automatically rotates the short-lived leaf
certificates it issues to each meshed pod - all internal to the Linkerd control plane, with no external
PKI or secret store required for the base mTLS guarantee this ADR needs. This is a materially
different model from, say, `cert-manager`-issued or Vault PKI-issued workload certs, which *do* require
an external CA dependency; Linkerd does not require one to deliver Layer 1 identity as described in
Section 2. (Istio's default Citadel-equivalent behaves the same way, for the same reason - noted here
because it was the alternative actually evaluated, not assumed away.)

Given that, **ADR-026 has no hard technical dependency on ADR-025.** They could ship in either order or
in parallel. We nonetheless sequence **Sprint 19 (this ADR) after Sprint 18 (ADR-025)**, for
operational rather than technical reasons:

* Sprint 18 is where the team first operates a new, security-critical, in-cluster control-plane
  component (Vault) end to end - install, policy authoring, verification. Sprint 19 repeats that same
  shape of work for a different component (Linkerd) immediately after; doing Vault first lets the team
  validate the "new security control plane on this specific Kind/Helm stack" pattern once before
  repeating it, rather than standing up two new control planes in the same window.
  * This is a scheduling choice, not a data dependency - if capacity allowed parallel work, nothing in
  this ADR would block it.
* It leaves a real, optional door open rather than closing it by default: a future hardening pass MAY
  point Linkerd's trust anchor at Vault's PKI secrets engine instead of Linkerd's self-managed root, for
  organizations that want one root-of-trust for both secrets and workload certs. That is materially
  easier to retrofit if Vault (ADR-025) already exists and is operationally proven; it is explicitly
  **not** required for this ADR's mTLS guarantee and is not part of this ADR's delivery scope.

---

## Consequences

### Positive

* Closes the specific residual risk `security-posture.md` Section 8 named and accepted for the MVP:
  in-cluster header forgery bypassing the gateway.
* Closes the mTLS line item and (paired with NetworkPolicy) the default-deny line item in Section 10's
  hardening checklist.
* No change to the JWT/RBAC model, `@PreAuthorize`, mediator `AuthorizationRule`, or any controller/
  handler code - Layer 1 (mesh) and Layer 2 (user identity) are additive and independent.
* Lower operational and resource overhead than Istio for this platform's actual (single-cluster,
  13-service) scale.
* mTLS is enabled by default for meshed traffic with no per-route policy authoring required for the
  base guarantee - the marginal YAML cost of the base case is small.

### Negative

* New in-cluster control plane to operate (a second one, after Vault in Sprint 18) - sidecar injection
  adds a proxy container to every pod, increasing per-pod resource requests/limits and pod count
  accounting relevant to the existing HPA (`min 2 / max 5 / 75% CPU`, Sprint 15.3) and PDB
  (`minAvailable: 1`) configuration, which will need to be re-verified with the sidecar's resource
  footprint included.
* Rollout ordering risk: pods must be meshed (proxy-injected) consistently, or a non-injected pod
  becomes the exact gap NetworkPolicy exists to cover (Section 3) - injection must be verified per
  service during Sprint 19, not assumed.
* Two trust layers (Section 2) is conceptually more moving parts for engineers to reason about than
  gateway-behind-trust alone, even though each layer individually is simple; onboarding/documentation
  must be explicit that they are independent (this ADR's Section 2 is written to be that reference).
* Does not address OPA/policy-as-code (TELCO-CRM-ADVANCED Section 4.1's fine-grained authz) - left for
  a future ADR if needed.

---

## Alternatives Considered

### Istio

Rejected for this platform's current scale; the heavier control plane and CRD surface serve
traffic-management capabilities (canary routing, multi-cluster, WASM) this platform does not need
today. Revisit if multi-cluster or complex traffic shaping becomes a real requirement.

### SPIFFE/SPIRE without a mesh

Rejected: shifts mTLS termination/rotation into every one of the 13 services individually, which is
significant repeated application-level engineering for an outcome sidecar injection gives for free,
and conflicts with the platform's "depend only on platform starters, do not hand-roll cross-cutting
infrastructure" discipline (ADR-018).

### mTLS via manual per-service Java KeyStore/TrustStore + cert-manager, no mesh

Rejected: closer in spirit to SPIFFE/SPIRE without a mesh - it would require every service's HTTP
client and server stack to be reconfigured for mTLS, and every certificate rotation to be handled
per-service rather than by a shared control plane. Strictly more engineering effort than a mesh for the
same guarantee.

### Defer NetworkPolicies to a separate future item

Rejected: `security-posture.md` Section 8 already frames mTLS and default-deny NetworkPolicy as one
combined production recommendation; delivering only the mTLS half would leave the exact gap (non-meshed
or misconfigured pods reachable in plain HTTP) that motivated the combined recommendation in the first
place.

### Require ADR-025 as a hard prerequisite (Vault-issued mesh certs from day one)

Rejected: Linkerd's self-managed trust anchor delivers this ADR's mTLS guarantee without an external
PKI dependency (Section 4); requiring Vault first would be an assumption, not a reasoned technical
dependency. Sequencing Sprint 19 after Sprint 18 is kept for operational reasons only, stated
explicitly rather than left implicit.

---

## Supersession

This ADR supersedes `docs/architecture/security-posture.md` Section 8 ("mTLS decision") in full: the
MVP deferral, its rationale, and its accepted residual risk are superseded by the decision above once
Sprint 19 delivers it (the deferral remains accurate and authoritative for the current, pre-Sprint-19
state). It closes the corresponding line item in Section 10's hardening checklist
(`[ ] mTLS for internal traffic (mesh or SPIFFE/SPIRE) + default-deny NetworkPolicies`). It also
supersedes ADR-011 Section 3 ("Service-to-Service Security... mTLS... Certificates are issued via
Kubernetes or internal PKI") by naming the concrete mechanism (Linkerd's self-managed Identity
component) ADR-011 left unspecified, and reconciles ADR-011 Section 6's "Services MUST trust mTLS
identity internally" with the fact that, as delivered, MVP internal traffic runs over plain HTTP under
gateway-behind-trust (per `security-posture.md`, which is the authoritative current-state record) -
that reconciliation resolves once Sprint 19 ships. `docs/product/TELCO-CRM-ADVANCED.md` Section 4.1's
"Service mesh and zero-trust (mTLS, SPIFFE, OPA policy-as-code)" proposed-ADR line is satisfied by this
ADR for the mesh/mTLS half; OPA policy-as-code remains future work.

---

## Related ADRs

* ADR-011 Security Foundation (user-identity/JWT model this ADR leaves unchanged; mTLS principle this
  ADR concretizes)
* ADR-010 Service Discovery and Configuration Strategy (Kubernetes-native service discovery this mesh
  layers onto)
* ADR-017 Service Template Standard (per-service `ServiceAccount` this ADR's mesh identity binds to)
* ADR-018 Platform Starter Dependency Model (reasoning against per-service bespoke TLS wiring, Section 1)
* ADR-021 PII and Data-Masking Strategy (unaffected - mTLS is a transport control, not a telemetry
  control; masking rules are unchanged)
* ADR-025 Secrets and Key Management (sequencing discussed in Section 4; no hard dependency)
