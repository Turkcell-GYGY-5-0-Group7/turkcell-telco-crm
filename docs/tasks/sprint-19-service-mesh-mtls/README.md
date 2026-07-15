# Sprint 19 - Service Mesh and mTLS (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| IN PROGRESS | 2/5 (19.3 and 19.4 authoring + static verification complete; live verification for both, plus all of 19.5, blocked on cluster access) | 2026-07-14 |

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
| 19.1 | Linkerd control-plane install (`deploy/helm/` release) + namespace injection annotation | DONE | [19.1-linkerd-control-plane-install-and-namespace-injection.md](19.1-linkerd-control-plane-install-and-namespace-injection.md) |
| 19.2 | Verify automatic sidecar injection and mTLS across all 13 services; re-verify HPA/PDB resource accounting with the sidecar's footprint | DONE | [19.2-verify-sidecar-injection-mtls-and-hpa-pdb-accounting.md](19.2-verify-sidecar-injection-mtls-and-hpa-pdb-accounting.md) |
| 19.3 | Per-service `Server`/`AuthorizationPolicy`: only the gateway's mesh identity may call downstream services; `/internal/**` remains edge-denied | IN PROGRESS (authoring + static verification done; live verification pending) | [19.3-per-service-server-authorizationpolicy-gateway-only.md](19.3-per-service-server-authorizationpolicy-gateway-only.md) |
| 19.4 | Default-deny `NetworkPolicy` per namespace + explicit allow rules matching the actual service-catalog call graph | IN PROGRESS (authoring + static verification done; live verification pending) | [19.4-default-deny-networkpolicy-and-explicit-allow-rules.md](19.4-default-deny-networkpolicy-and-explicit-allow-rules.md) |
| 19.5 | Live verification on the Sprint 15 Kind cluster: forged-header bypass attempt fails; legitimate gateway-to-service and Kafka/Postgres/Redis traffic is unaffected | BLOCKED (19.5.3 done; 19.5.1/19.5.2 need a reachable cluster) | [19.5-live-verification-forged-header-bypass-and-smoke-test.md](19.5-live-verification-forged-header-bypass-and-smoke-test.md) |

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

## 19.2 Verification Record (2026-07-13, live on Kind)

Live-verified end to end on the same `kind-telco` cluster Feature 19.1 stood up (Linkerd control
plane already installed, `telco` namespace already annotated). All 13 services were freshly
deployed via the real `deploy/helm/telco-service` chart + per-service values (fresh install, not a
restart of pre-existing pods, so 19.1.2's "injection only applies to pods scheduled after the
namespace annotation" risk was exercised for real) with `vault.enabled=false` (local-dev default,
Sprint 18 out of scope here) and locally-built `:local`-tagged images loaded into the Kind node.

**Config-server duplicate-key bug (Sprint 15/18 open question) - RESOLVED, root cause found: a
stale local image, not a current source defect.** A domain-engineer agent's investigation (that it
could not reproduce standalone) is CONFIRMED as a non-issue in current `microservices/configs/`
source, but the failure DID resurface on this fresh in-cluster deploy with the pre-existing
`telco-config-server:local` image (built 2026-07-04, 9 days stale): config-server logged
`FailedToConstructEnvironmentException: ... found duplicate key spring` for every service on the
`dev,docker` profile combo, and `api-gateway` crash-looped on the resulting 500. Root cause,
confirmed by extracting `/configs/application-dev.yml` from inside the running container: the
stale image's baked copy had two separate top-level `spring:` blocks (an invalid YAML artifact from
whatever was on disk at that earlier build time), while the current repo file
(`microservices/configs/application-dev.yml`) has always had exactly one. Rebuilding the
config-server image fresh from current `HEAD` (`docker build -f
microservices/config-server/Dockerfile`) produces a `/configs/application-dev.yml` with a single
`spring:` block and serves clean `200`s for all 12 consuming services across the `dev,docker`
profile combo - verified via `curl -u config:config http://config-server:8888/<service>/dev,docker`
for all 12. No source change was needed or made to `microservices/configs/`; the fix was rebuilding
and redeploying the image. **Conclusion: confirmed-non-issue in current source, stale-image
root-caused and fixed for this verification pass** - any environment still running a
pre-2026-07-13-rebuild `config-server` image should rebuild it.

**Two additional real bugs found and fixed live** (both pre-existing, newly exposed by this
sprint's first full 13-service in-cluster boot, unrelated to the mesh itself):

1. **Kubernetes service-link env var collision (`enableServiceLinks`)**: `api-gateway` crash-looped
   with `NumberFormatException: For input string: "tcp://10.96.x.x:6379"` binding
   `spring.data.redis.port`. Root cause: Kubernetes auto-injects a `REDIS_PORT=tcp://<clusterIP>:6379`
   env var for the `redis` Service into every pod in the namespace (`enableServiceLinks` defaults to
   `true`), colliding with the platform's own `REDIS_PORT=6379` (plain int) config-server convention.
   The exact same bug class was already fixed for `schema-registry` in `deploy/helm/dependencies`
   (PR #20) but never applied to `deploy/helm/telco-service` (used by all 13 services). Fixed by
   adding `enableServiceLinks: false` to `deploy/helm/telco-service/templates/deployment.yaml` (all
   13 services; none use K8s service-link env vars - service discovery is via Eureka/DNS).
2. **CORRECTION (2026-07-13, caught and reverted before merge - not a real bug):** this feature's
   original verification pass claimed the 10 domain services' `SPRING_PROFILES_ACTIVE: dev,k8s` was a
   silent no-op ("no `microservices/configs/application-k8s.yml` exists") and changed all 10 values
   files to `dev,docker`. That claim was factually wrong -
   `microservices/configs/<service>/application-k8s.yml` exists for all 10 services (verified present
   on disk), added deliberately in **Sprint 18 Feature 18.5** specifically so DB credentials come from
   Vault/CSI-sourced `${SERVICE}_DB_USER`/`${SERVICE}_DB_PASSWORD` env vars instead of the `docker`
   profile's hardcoded plaintext DB block (ADR-025) - each values file even carries an explicit
   `# Feature 18.5: moved off dev,docker (plaintext DB block) to dev,k8s` comment saying so, which the
   original pass did not reconcile before editing. The observed symptom (Loki/Kafka/Keycloak overrides
   not applying) was real for *this specific verification's* `vault.enabled=false` setup, where nothing
   populates the `k8s` profile's `${SERVICE}_DB_USER}`-style placeholders - an artifact of testing
   without Vault wired in, not evidence the profile is dead. All 10 values files have been reverted
   back to `dev,k8s` (see [../lessons.md](../lessons.md) 2026-07-13 entry). A real follow-up this
   surfaced, still open: `dev,k8s` + `vault.enabled=false` is not currently a supported combination for
   a Vault-free mesh-only verification pass like this one - out of Sprint 19's scope to fix (Sprint 18
   territory), noted here so a future session doesn't re-hit the same false conclusion.

**19.2.1 - sidecar injection and mTLS (AC met).** `kubectl -n telco get pods -o jsonpath=...`
confirmed `linkerd-proxy` alongside the app container for all 13 services' pods (plus every
dependency - postgres, redis, mongo, minio, keycloak - since the whole `telco` namespace is
annotated). `linkerd check --proxy` and `linkerd viz check` both fully green. Installed
`linkerd-viz` (deferred in 19.1, needed here) and generated real HTTP traffic from `api-gateway` to
`identity-service`, `customer-service`, `product-catalog-service`, and `ticket-service` (via
in-cluster service DNS, actual ports per each service's K8s Service, not assumed 8080).
`linkerd viz edges deploy -n telco` and `linkerd viz stat deploy -n telco` show `SECURED=true` /
100% success for all four gateway-to-domain-service edges. **Backend note (differs from the
spec's assumption): in this deployment Postgres is NOT a plaintext exception** - because the
`dependencies` chart also installs into the `telco` namespace (annotated the same as the services),
Postgres is meshed too, and `linkerd viz edges po -n telco` shows `identity-service -> postgres-0`,
`customer-service -> postgres-0`, etc. all `SECURED=true`. So end-to-end mTLS (gateway to service
to database) is real here, not just gateway-to-service - a stronger posture than the spec's default
assumption, not a gap.

**19.2.2 - HPA re-verification with sidecar footprint (AC met, real bug found and fixed).**
Installed `metrics-server` (patched `--kubelet-insecure-tls` for Kind, per RUNBOOK Section 2.3).
Measured `linkerd-proxy`'s actual resource footprint on `api-gateway`: **100m CPU / 20Mi memory
request, ~1-2m CPU actual usage under both idle and load** (`kubectl top pods --containers`).
**CPU-accounting finding**: the chart's original HPA metric (`type: Resource`, pod-level) sums
usage AND requests across all containers in the pod - so utilization% became
`(app_usage + ~1m) / (500m + 100m)` instead of `app_usage / 500m`. Since the sidecar's *request*
(100m, +20% of the app's 500m request) inflates the denominator far more than its near-zero actual
usage inflates the numerator, the net effect is **dilution, not inflation**: the same real app load
now reads as a LOWER utilization% than pre-mesh, meaning scale-out is delayed (needs ~450m of real
app usage to hit 75% of 600m, vs. 375m of 500m pre-mesh) - a materially later trigger point, not a
premature one. **Remediation decision: switched to the `ContainerResource` metric type** (GA in
this cluster's Kubernetes 1.31), scoped to the app container by name, in
`deploy/helm/telco-service/templates/hpa.yaml` (both the `cpu` and `memory` metrics) - this excludes
`linkerd-proxy` from the calculation entirely, the cleanest fix and forward-compatible with any
future sidecar. Applied live via `helm upgrade`; the API server accepted it immediately (no version
gap). Verified end to end **twice** - once against the original pod-level metric (to observe the
bug), once against the fixed container-level metric (to prove the fix): both times, a
`busybox`-based in-cluster load generator hitting `api-gateway`'s `/actuator/health` in a tight loop
drove real scale-out `2 -> 4 -> 5` (max) via `SuccessfulRescale` events, and stopping the load drove
real scale-in `5 -> 4 -> 3 -> 2` (min) at the documented 30s/pod cadence after the 60s stabilization
window - `kubectl describe hpa api-gateway` shows both `SuccessfulRescale ... cpu container resource
utilization (percentage of request) above target` (scale-out) and `... All metrics below target`
(scale-in) events. Target restored to the shipped `75%` after the test.

**19.2.3 - PDB and rolling-update re-verification with sidecar footprint (AC met).** Used the real
Eviction API (`kubectl create --raw .../eviction`), matching Sprint 15.3's methodology exactly.
Against `ticket-service` (`minAvailable: 1`, 1 replica - resource-constrained tuning, see note
below): eviction of the sole replica returned `429 TooManyRequests: Cannot evict pod as it would
violate the pod's disruption budget` immediately - the last replica cannot be taken, mesh sidecar
present. Against `api-gateway` (`minAvailable: 1`, 2 replicas): first eviction returned `201`
(succeeded, one replica remained), the immediately-following second eviction attempt (before the
replacement pod was Ready) returned `429` with the same disruption-budget message - identical
behavior to the pre-mesh Sprint 15.3 result. Zero-outage rolling update: ran a real
`helm upgrade api-gateway` (annotation bump, triggering a genuine `RollingUpdate`,
`maxUnavailable: 0` / `maxSurge: 1`) while an in-cluster `busybox` pod polled
`http://api-gateway:8080/actuator/health` once per second throughout - **300/300 requests
succeeded, 0 connection failures**, confirming continuous availability through the rollout with the
mesh active.

**Known environment constraint (not a mesh, HPA, or PDB defect)**: the shared Kind node (Docker
Desktop VM, ~7.7Gi memory / 12 vCPU) could not sustain all 13 services at their default
`minReplicas: 2` simultaneously alongside the full dependency stack, the full observability stack
(Grafana/Loki/Tempo/Prometheus/otel-collector), and `linkerd-viz` at once - this is the same class
of single-node CPU/memory contention Sprint 15.3 already documented (`docs/tasks/STATUS.md`,
"kafka-0's exit-143 churn was a liveness-probe kill under single-node CPU pressure"), reproduced
here at larger scale because the mesh sidecar adds a second container (and a fixed 100m CPU / 20Mi
memory reservation) to every one of the ~27 pods in the namespace. Verification proceeded by
temporarily scaling the observability stack and `kafka-connect`/`kafka`/`schema-registry` to 0
replicas and setting 10 of 13 domain services' HPA `minReplicas` to 1 via `kubectl patch hpa`
(an imperative, non-persisted cluster-state change - the chart itself still declares
`minReplicas: 2` by default and this is not a values-file or chart edit) to free enough headroom for
`api-gateway` to reach `maxReplicas: 5` during the HPA test without starving the node. This is a
capacity limitation of this specific verification machine, not a product or chart defect; a cluster
with more headroom (a larger Kind node, or a real multi-node cluster) would not need this
adjustment.

## 19.3 Authoring and Static Verification Record (2026-07-14)

Continued from the 19.2 verification pass above. This pass authored 19.3.1/19.3.2's chart templates
and per-service overrides, and completed 19.3.3's confirmatory audit, all statically (no Kind cluster
was reachable in this session - Docker Desktop was not running and `helm`/`linkerd` were not on the
session's `PATH`). **Live verification of policy existence/activation on a running cluster, required by
this feature's Definition of Done, is deferred** to the next session that has cluster access (or folded
into Feature 19.5's live pass, which needs the cluster up regardless).

**19.3.1/19.3.2 - templates and per-service overrides (authored, statically reviewed).**
`deploy/helm/telco-service/templates/server.yaml` renders one `policy.linkerd.io/v1beta1` `Server` per
service unconditionally, selecting the release's pod labels and the `http` named container port -
verified to match `templates/deployment.yaml`'s container port name exactly.
`templates/authorizationpolicy.yaml` + `templates/meshtlsauthentication.yaml` are gated on
`.Values.meshPolicy.enabled` (default `true`) and expand `meshPolicy.authorizedClients` (default
`[api-gateway]`) into `<name>.telco.serviceaccount.identity.linkerd.cluster.local` mesh identities.
`api-gateway` sets `meshPolicy.enabled: false` (its real caller, Kind's ingress-nginx, is unmeshed and
has no mesh identity to present) but still gets a bare `Server` via 19.3.1's unconditional template.
`config-server`/`discovery-server` override `authorizedClients` to all 13 service names (their real
caller set - every service fetches config and resolves discovery on startup).

**Cross-service caller audit (Explore-agent verification, this session).** Grepped every service under
`microservices/` for `RestTemplate`/`WebClient`/`RestClient`/`@FeignClient`-style HTTP client classes
targeting another domain service (no `@FeignClient` usage exists in this repo at all). Found exactly
five real cross-service synchronous calls, matching the three services already carrying a
`meshPolicy.authorizedClients` override in `deploy/helm/values/`:
`customer-service <- order-service` (`CustomerServiceClient`), `order-service <- subscription-service`
(`OrderServiceClient`), and `product-catalog-service <- {order-service, billing-service,
usage-service}` (`ProductCatalogServiceClient`, `ProductCatalogBillingClient`, `ProductCatalogClient`).
No domain service outside this list has an undocumented non-gateway caller; the three existing
overrides are complete and no fourth override is needed.

**19.3.3 - `/internal/**` edge-deny audit (AC met, confirmatory only, no code change).** The deny lives
in `microservices/api-gateway/.../config/GatewaySecurityConfig.java` (`.requestMatchers("/internal/**",
"/__gateway_blocked").permitAll()`, paired with `GatewayRouteConfig`'s local-404-sink router function) -
confirmed present and, per `git status`/`git diff`, untouched by this sprint's changes (no file under
`microservices/api-gateway/` appears in the working tree diff). Confirmed the new mesh
`Server`/`AuthorizationPolicy`/`MeshTLSAuthentication` templates operate exclusively on Linkerd mTLS
client-certificate identity (`requiredAuthenticationRefs`), a sub-HTTP layer with no HTTP-path matching
of any kind, so they cannot grant a path-level exception that lets an `/internal/**` request through -
the two controls (gateway HTTP-routing edge-deny, mesh mTLS-identity policy) remain independent, per
ADR-026 Section 2's "two distinct trust layers" framing. `GatewaySecurityConfig` carries zero code
changes attributable to this sprint, satisfying the subtask's AC.

**Deferred to a cluster-available session (or Feature 19.5):** `kubectl -n telco get server` /
`get authorizationpolicy` / `get meshtlsauthentication` listing one object per service as expected;
`linkerd -n telco authz deploy/<service>` showing each `Server`'s active policy; a live request from a
non-gateway pod's mesh identity to a domain service being rejected; a live request from the
`api-gateway` pod's mesh identity succeeding unchanged. None of these require further authoring - the
templates and values are complete and statically consistent - only a reachable Kind cluster with Linkerd
installed (19.1) to execute against.

## 19.4 Authoring and Static Verification Record (2026-07-14)

Same session and same constraint as 19.3's record above: no Kind cluster was reachable (Docker
Desktop down, `helm`/`kubectl`/`linkerd` unavailable in this session's shell), so authoring and
review were entirely static (chart inspection and cross-file consistency checks, no
`helm template`/`lint`/`install`).

**19.4.1 - default-deny baseline (authored).**
`deploy/helm/dependencies/templates/networkpolicy-default-deny.yaml` adds a `podSelector: {}`,
`policyTypes: [Ingress, Egress]`, no-rules `NetworkPolicy` plus a co-located universal CoreDNS
egress-allow `NetworkPolicy` (CoreDNS runs in `kube-system`, `k8s-app: kube-dns`, matched via the
Kubernetes-standard automatic `kubernetes.io/metadata.name` namespace label). Placed in the
`dependencies` chart, not `telco-service`, because `podSelector: {}` must select every pod in
`telco` including the dependency chart's own stateful backends - no single `telco-service` release
can express a namespace-wide selector. Gated by a new `networkPolicy.enabled` flag in
`deploy/helm/dependencies/values.yaml` (default `true`).

**19.4.2 - ingress allow rules (authored).**
`deploy/helm/telco-service/templates/networkpolicy-ingress.yaml` renders one `NetworkPolicy` per
service release. By default it reuses `.Values.meshPolicy.authorizedClients` (the exact list 19.3's
Linkerd `AuthorizationPolicy` already authorizes) as the ingress source set, via a new
`networkPolicy.useAuthorizedClientsForIngress` flag (default `true`) - deliberately one shared list
rather than two independently-maintained ones, so the network-layer and mesh-layer controls cannot
silently drift apart. `api-gateway` is the sole exception
(`deploy/helm/values/api-gateway.yaml`: `useAuthorizedClientsForIngress: false`,
`externalIngress: true`), sourcing from the Kind `ingress-nginx` controller's namespace/labels
instead, since that controller is un-meshed and not a `telco-service` release.

**19.4.3 - egress allow rules (authored).**
`deploy/helm/telco-service/templates/networkpolicy-egress.yaml` renders per-service egress rules
gated by `networkPolicy.egress.{postgres,redis,kafka,minio,mongo,keycloak}` flags, set in each
`deploy/helm/values/<service>.yaml` per `docs/architecture/service-catalog.md` Section 5's
Infrastructure Profile table, cross-checked against `docs/architecture/event-catalog.md`'s
producer/consumer roster for the `kafka` flag. Egress to `config-server`/`discovery-server` is
rendered unconditionally per release (mirroring 19.4.2's ingress rule from the caller's side, minus
the two infra services' own releases). Three deviations from a literal Section 5 reading, each
verified against the actual shipped config rather than assumed:
  - `subscription-service` and `billing-service` get `redis: true` beyond what Section 5's Cache
    column lists, for their Redisson-backed distributed lock (ADR-024) - a real egress dependency
    the cache-only framing does not capture.
  - `usage-service`/`payment-service` keep `redis: true` per Section 5's designed dependency even
    though dev/k8s profile does not yet wire `REDIS_HOST` (only prod/staging do) - granting now
    avoids a second NetworkPolicy change later; an unused allow is not a security regression the way
    a missing one would be.
  - `keycloak: true` is set for every domain service, not only `api-gateway`/`identity-service`:
    confirmed live (this session) via `microservices/configs/<service>/application-docker.yml`
    that every domain service is its own Spring Security OAuth2 resource server with a directly
    configured `jwks-uri` against Keycloak - additional to, not a replacement for, the gateway's own
    JWT validation and header injection (ADR-011 unchanged).

**Known architecture-level limitation, flagged not fixed (out of this Helm-only feature's scope):**
this stack runs one shared Postgres StatefulSet serving multiple logical databases (one per service,
via initdb SQL), not one Postgres pod per service - so 19.4.3's literal AC wording ("customer-service
cannot reach another service's Postgres *instance*") cannot be enforced at the network layer, because
all services share the same Postgres pod/port. Database-per-service isolation here is logical
(ADR-006, separate schemas/credentials), not physical - a pre-existing architecture fact this
feature did not introduce and is not in scope to change. Worth a `tech-lead` note if the literal AC
needs re-scoping to "logical isolation, not network-layer pod isolation."

**Deferred to a cluster-available session (or folded into Feature 19.5):** `kubectl -n telco get
networkpolicy` listing the expected objects; a live connectivity test proving the default-deny
baseline blocks all traffic before allow rules are added (19.4.1's AC); live proof that
api-gateway-to-service, service-to-config/discovery, and each service's own infra egress succeed
while a non-authorized pod's direct call is blocked (19.4.2/19.4.3's ACs).

## 19.5.3 Verification Record (2026-07-14) - zero change to the JWT/RBAC user-identity trust layer

Ran a repository-wide diff audit of every file touched across Sprint 19 (Features 19.1-19.4,
uncommitted): `git status --porcelain` lists changes confined to `deploy/`, `docs/tasks/`,
`docs/tasks/sprint-19-service-mesh-mtls/`, and `architecture/adr/ADR-026-service-mesh-and-mtls.md`
(its `Status: Proposed` -> `Accepted` flip) - zero `.java` files, zero paths under any service's
`security`/`config` package, zero mediator `AuthorizationRule` implementations. The seven domain
services' `deploy/helm/values/*.yaml` entries that showed as modified in `git status` but carry no
`meshPolicy`/`networkPolicy` override (`billing-service`, `identity-service`,
`notification-service`, `payment-service`, `subscription-service`, `ticket-service`,
`usage-service`) were confirmed via `git diff` to be line-ending-only noise (LF/CRLF), zero content
change. **19.5.3's AC is met**: this sprint's entire changeset is chart/manifest/docs-only: the
user-identity trust layer (ADR-011: Keycloak JWT issuance, gateway validation,
`X-User-Id`/`X-User-Roles` injection, `@PreAuthorize`, mediator `AuthorizationRule`) is verified
unchanged. (19.5.1 and 19.5.2 remain blocked on cluster access - see the Features table above.)

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
