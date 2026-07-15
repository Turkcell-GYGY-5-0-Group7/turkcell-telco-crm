# Telco CRM - Operations Runbook (Sprint 15.5)

This is the single operational entry point for deploying and running the Telco CRM
platform on Kubernetes. Following only this document, a clean environment can be
brought up, deployed, scaled, rolled back, and observed. It reflects procedures
that were **live-verified on a Kind cluster** during Sprint 15.

Related documents (this runbook links to them; it does not duplicate them):

- Chart layout, install order, config/secret model: [`deploy/helm/README.md`](helm/README.md)
- Rollback detail and evidence: [`deploy/ROLLBACK.md`](ROLLBACK.md)
- Post-deploy smoke test: [`deploy/smoke/smoke-test.sh`](smoke/smoke-test.sh)
- CI deploy pipeline: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)

---

## 1. Prerequisites

| Tool | Version verified | Purpose |
| --- | --- | --- |
| Docker | Desktop / Engine (running) | container runtime; Kind runs the node as a container |
| kind | v0.33 | local Kubernetes cluster |
| helm | v4.2 | chart install / upgrade / rollback |
| kubectl | v1.34 | cluster access |
| jq, curl | any recent | smoke test |

The container images `ghcr.io/<owner>/telco-<service>:<tag>` are built and pushed
by CI (`.github/workflows/ci.yml`, task 15.1.2). For a local Kind cluster you can
instead build images locally and `kind load` them (Section 5.2).

> On Windows Git Bash, disable MSYS path mangling for `kubectl --raw` URLs with
> `export MSYS_NO_PATHCONV=1` (but not when passing local file paths to Windows
> binaries - it also disables the `/tmp` -> Windows-temp translation those need).

---

## 2. Bring up a cluster (local Kind)

```sh
# 2.1 Create the cluster (host 18080->ingress 80, 18443->443).
kind create cluster --name telco --config deploy/kind/kind-cluster.yaml

# 2.2 Ingress controller (required for the gateway Ingress).
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

# 2.3 metrics-server (required for HPA). Patched for Kind's self-signed kubelet TLS.
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n kube-system rollout status deploy/metrics-server --timeout=120s

# 2.4 Linkerd (19.1.1, ADR-026 Section 1) - the service mesh control plane.
#     Installed here, before Vault/dependencies/any service, so that the
#     FIRST pod ever created in `telco` is already meshed - avoiding exactly
#     the "rollout ordering risk" ADR-026's Consequences section calls out
#     (a pod created before the mesh/annotation exists starts unmeshed and
#     must be restarted later to pick up the sidecar). Two charts, CRDs
#     first, per Linkerd's official install model; installs into its own
#     `linkerd` namespace, not `telco`. Self-managed trust anchor (ADR-026
#     Section 4) - NOT cert-manager, NOT Vault's PKI: generate the root
#     CA/issuer cert locally and NEVER commit it (same handling as Vault's
#     unseal keys/root token, Section 3 below).
#     The `telco` namespace is created AND annotated here (idempotent)
#     rather than waiting for Section 5.1's dependency-chart install, since
#     Vault (2.5) needs the namespace to exist first and every pod created
#     into it from this point on should be meshed.
kubectl create namespace telco --dry-run=client -o yaml | kubectl apply -f -
kubectl annotate namespace telco linkerd.io/inject=enabled --overwrite
kubectl create namespace linkerd --dry-run=client -o yaml | kubectl apply -f -
helm dependency update deploy/helm/linkerd-crds
helm install linkerd-crds deploy/helm/linkerd-crds -n linkerd
helm dependency update deploy/helm/linkerd-control-plane
bash deploy/helm/linkerd-control-plane/generate-trust-anchor.sh /tmp/linkerd-trust
helm install linkerd-control-plane deploy/helm/linkerd-control-plane -n linkerd \
  --set-file linkerd-control-plane.identityTrustAnchorsPEM=/tmp/linkerd-trust/ca.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.crtPEM=/tmp/linkerd-trust/issuer.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.keyPEM=/tmp/linkerd-trust/issuer.key
kubectl -n linkerd wait --for=condition=ready pod --all --timeout=180s
linkerd check   # every control-plane check must be green before proceeding

# 2.5 Vault (18.1, ADR-025) - singleton secret store, installed alongside the
#     dependency stack (Section 5.1) and waited on here, ahead of any service
#     install, the same way ingress-nginx/metrics-server are waited on above.
#     `vault.csi.enabled: true` (18.3) also deploys the Vault CSI provider
#     DaemonSet as part of this release - waited on in step 2.6 below, after
#     the CSI driver it registers against is installed. Because the `telco`
#     namespace was already annotated in 2.4, the Vault pod itself comes up
#     meshed (2 containers) - confirm with
#     `kubectl -n telco get pod -l app.kubernetes.io/name=vault` if you want
#     to see it; this does not change any Vault procedure below.
helm dependency update deploy/helm/vault
helm install vault deploy/helm/vault -n telco
# NOTE: `kubectl rollout status statefulset/vault` does NOT work here - the
# upstream hashicorp/vault chart uses updateStrategyType: OnDelete, which
# `rollout status` explicitly refuses to track. Waiting on the Ready condition
# doesn't work either: Vault's readiness probe runs `vault status`, which
# exits non-zero while sealed, so a freshly-installed pod is Running but
# never becomes Ready until AFTER the unseal procedure below - waiting on
# Ready here would hang until Section 3 is done. `condition=Initialized`
# is what actually confirms the container started and is live-verified.
kubectl -n telco wait --for=condition=Initialized pod -l app.kubernetes.io/name=vault --timeout=180s

# 2.6 Secrets Store CSI Driver + Vault CSI provider (18.3, ADR-025 Section 1) -
#     the runtime component that syncs each service's SecretProviderClass
#     (18.3.2) into a native Kubernetes Secret. Installed ahead of any service
#     release that sets vault.enabled=true. Both driver and provider are
#     DaemonSets - Ready on every node is the acceptance criterion (18.3.1).
helm dependency update deploy/helm/csi-driver
helm install csi-driver deploy/helm/csi-driver -n telco
kubectl -n telco rollout status daemonset/csi-driver-secrets-store-csi-driver --timeout=180s
kubectl -n telco rollout status daemonset/vault-csi-provider --timeout=180s
kubectl get csidriver secrets-store.csi.k8s.io
```

A freshly-installed Vault pod is running but **sealed** - the wait above only
confirms the container started, not that Vault is initialized/unsealed (see
the NOTE above for why `Ready` isn't the right condition to wait on here). Run
the one-time init/unseal procedure now (Section 3) before moving on to
Section 5's service installs.

For a managed cluster (EKS/GKE/AKS), skip 2.1; use the provider's ingress and a
real metrics-server, and point a DNS name at the ingress instead of `telco.local`.

---

## 3. Vault initialization and unseal

Task 18.1.2 (ADR-025 Section 1). A freshly-installed Vault pod (Section 2.5) is
running but **sealed** - it holds no usable key material until an operator runs
this one-time procedure. This is a manual, Shamir-sealed, single-node
procedure by design: ADR-025 Section 1 accepts "a single-node Vault with
Shamir-sealed manual unseal ... as an accepted MVP posture" for this
platform's current scale; auto-unseal (cloud KMS / Transit) is the documented
production upgrade path, not required here.

```sh
# 3.1 Initialize (one time only - re-running against an already-initialized
#     Vault fails loudly, which is the desired behavior).
kubectl -n telco exec -it vault-0 -- vault operator init -key-shares=5 -key-threshold=3
```

This prints 5 **Unseal Keys** and one **Initial Root Token**. Copy all of them
out of the terminal immediately - Vault does not store them and does not
re-print them.

```sh
# 3.2 Unseal (needs 3 of the 5 keys, per -key-threshold above). Run the exec
#     three times, each with a different unseal key.
kubectl -n telco exec -it vault-0 -- vault operator unseal   # paste Unseal Key 1
kubectl -n telco exec -it vault-0 -- vault operator unseal   # paste Unseal Key 2
kubectl -n telco exec -it vault-0 -- vault operator unseal   # paste Unseal Key 3

# 3.3 Verify.
kubectl -n telco exec -it vault-0 -- vault status
#   -> Initialized: true, Sealed: false
```

**Unseal keys and the root token are DEV-ONLY-handled here and are NEVER
committed to this repository** - the same posture `deploy/helm/README.md` and
Section 4 below already state for every other secret in this platform. Do not
paste them into a values file, a ConfigMap, a commit, or a CI log. Store them
operationally the same way any other production credential is handled outside
version control (a password manager or a dedicated secrets vault external to
this cluster - ironic but true for the key material that unseals Vault itself);
for a real (non-Kind) environment, replace this manual Shamir procedure with
auto-unseal (cloud KMS / an upstream Vault's Transit engine) rather than
distributing raw key shares to operators.

If the Vault pod restarts (node drain, upgrade, crash), it comes back up
**sealed again** - Integrated Storage (Raft) persists the data but not the
unseal state. Repeat only step 3.2 (3 of the 5 keys); step 3.1 (`init`) is
one-time for the life of the Raft data volume.

Once unsealed, enable the Kubernetes auth method and per-service policies/
roles (task 18.2) - see Section 13 ("Vault Kubernetes auth method and
per-service policies") before any service is expected to authenticate to
Vault.

---

## 4. Configuration and secrets

Full model in [`deploy/helm/README.md`](helm/README.md) ("Config / Secret model").
Summary:

- Per-service **ConfigMap** (`<service>-config`) carries non-secret env; per-service
  **Secret** (`<service>-secret`) carries credentials/keys (`ENCRYPT_KEY`,
  `*_PASSWORD`, `CUSTOMER_AES_KEY`). Both projected via `envFrom` - unchanged
  regardless of the Secret's source (below).
- **`vault.enabled: true` (Vault-backed, PRIMARY path for any non-local
  environment, 18.3/18.4, ADR-025 Section 1)**: the Secret is synced from
  Vault KV v2 by the Secrets Store CSI Driver + Vault CSI provider (Section
  2.5), via a per-service `SecretProviderClass`
  (`templates/secretproviderclass.yaml`) mounted as a CSI volume on the pod.
  Requires Vault initialized/unsealed (Section 3), its Kubernetes auth method
  + per-service policies bootstrapped (Section 13), and the five core
  credentials seeded into KV v2 (Section 14, task 18.4.1) first. See
  `deploy/helm/README.md` "Config / Secret model" for the full flow and the
  per-service `vault.secretKeys` values shape. **This is the required mode
  for staging/production** - `vault.enabled=false` MUST NOT be used outside a
  local/dev cluster.
- **`vault.enabled: false` (LOCAL-DEV-ONLY, default)**: the values under
  `secrets:` in `deploy/helm/values/*.yaml` are obvious, non-random
  placeholder defaults (`config`/`eureka`/`telco`, a repeating-pattern
  `ENCRYPT_KEY`/`CUSTOMER_AES_KEY`), rendered into a static Secret by
  `templates/secret.yaml`, that exist only so a from-scratch local Kind
  cluster boots without needing Vault first. **No production secret is, or
  has ever been, committed here** - since 18.4.2 none of these placeholder
  values could plausibly be mistaken for real key material. Do not override
  these at install time for anything other than local development; for any
  shared/non-local environment, install with `vault.enabled=true` instead of
  overriding the static defaults.
- Dependency credentials (postgres/redis/minio/keycloak/grafana) live in the
  `dependencies` chart's Secrets - override the same way for non-dev (not yet
  Vault-sourced; tracked as a future follow-up, out of Sprint 18's scope).
- config-server serves the bulk of runtime config from its baked `/configs`
  (ADR-010); it resolves `${...}` host placeholders from its own env. (Fully
  replacing config-server with direct ConfigMaps is a ratified post-MVP change.)

**Coordination note for the `security` agent**: this closes the "Real secrets
from Vault/K8s Secret" line item in `docs/architecture/security-posture.md`
Section 10's hardening checklist (ADR-025's Supersession section names this
explicitly). This runbook and `deploy/helm/vault/seed-secrets.sh` are the
evidence; `security-posture.md` Section 10 itself is intentionally NOT edited
by this feature (out of `deploy/` scope) and should be updated by the
`security` agent to mark that checklist item closed.

---

## 5. Deploy the platform

### 5.1 Dependencies + services

```sh
# 5.1.1 Dependency stack (all stateful backends). The `telco` namespace was
# already created AND annotated `linkerd.io/inject: enabled` in Section 2.4
# (ahead of the Vault install, 19.1.2/ADR-026); the create here is idempotent
# in case Section 2.4 was skipped, and does not remove the annotation
# (verified live - a `kubectl annotate`-added annotation survives a later
# `kubectl create ns --dry-run=client -o yaml | kubectl apply -f -` because
# apply's three-way merge only reconciles fields present in
# `last-applied-configuration`, which this bare create/apply never sets for
# annotations added out-of-band). Give it a long timeout: images are large on
# first pull and it has a MinIO bucket-create post-install hook.
kubectl create namespace telco --dry-run=client -o yaml | kubectl apply -f -
helm install telco-deps deploy/helm/dependencies -n telco --set createNamespace=false --timeout 20m

# Wait for the core deps (schema-registry is a known follow-up - see Section 12).
for d in postgres redis mongo kafka keycloak minio; do
  kubectl -n telco rollout status "statefulset/$d" 2>/dev/null || \
  kubectl -n telco wait --for=condition=ready pod -l "app.kubernetes.io/name=$d" --timeout=300s
done

# 5.1.2 Services - install order and the loop are in deploy/helm/README.md.
#       Set the GHCR owner and pin the image tag to the CI commit sha.
OWNER=<owner-lowercased>; TAG=sha-<12-char-sha>
helm upgrade --install config-server    deploy/helm/telco-service -n telco -f deploy/helm/values/config-server.yaml    --set image.owner=$OWNER --set image.tag=$TAG
helm upgrade --install discovery-server deploy/helm/telco-service -n telco -f deploy/helm/values/discovery-server.yaml --set image.owner=$OWNER --set image.tag=$TAG
helm upgrade --install api-gateway      deploy/helm/telco-service -n telco -f deploy/helm/values/api-gateway.yaml      --set image.owner=$OWNER --set image.tag=$TAG
for s in identity-service customer-service product-catalog-service order-service \
         subscription-service usage-service billing-service payment-service \
         notification-service ticket-service; do
  helm upgrade --install "$s" deploy/helm/telco-service -n telco -f "deploy/helm/values/$s.yaml" --set image.owner=$OWNER --set image.tag=$TAG
done
```

### 5.1.3 Mesh verification (19.1, ADR-026)

Every pod above came up meshed automatically (2 containers: the service's own
+ `linkerd-proxy`) because of the `telco` namespace annotation applied in
Section 2.4 - no per-service chart change. Confirm:

```sh
kubectl -n telco get pod -l app.kubernetes.io/name=api-gateway \
  -o jsonpath='{.items[0].spec.containers[*].name}'
# -> api-gateway linkerd-proxy   (2 containers)

linkerd check --proxy   # data-plane checks green, incl. "data plane proxies certificate match CA"
```

Per-service `Server`/`AuthorizationPolicy` restricting inbound traffic to
meshed+authenticated callers, and the default-deny `NetworkPolicy` compensating
control, are Features 19.2-19.4 (not this feature) - `linkerd check --proxy`
above only confirms the mesh/mTLS layer itself is healthy, not that
authorization policies are yet in place.

### 5.2 Local Kind: use locally-built images

When testing on Kind without GHCR access, build an image, load it into the node,
and point the release at it with `pullPolicy=Never`:

```sh
docker build -f microservices/<svc>/Dockerfile -t telco/<svc>:kind .
kind load docker-image telco/<svc>:kind --name telco
helm upgrade --install <svc> deploy/helm/telco-service -n telco -f deploy/helm/values/<svc>.yaml \
  --set image.registry=docker.io --set image.owner=telco --set image.repository=<svc> \
  --set image.tag=kind --set image.pullPolicy=Never
```

---

## 6. Access the platform

```sh
# Gateway via the Ingress. Map telco.local to the ingress, or send the Host header.
curl -H "Host: telco.local" http://localhost:18080/actuator/health      # -> {"status":"UP"}
# (or add "127.0.0.1 telco.local" to /etc/hosts and use http://telco.local:18080)

# All other services are ClusterIP-only - reach them through the gateway
# (/api/v1/...) or via `kubectl -n telco port-forward svc/<name> <local>:<port>`.
```

Authenticated call (Keycloak realm `telco-crm`, ROPC via the `telco-web` client):

```sh
kubectl -n telco port-forward svc/keycloak 8085:8080 &
TOKEN=$(curl -s -X POST http://localhost:8085/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telco-web \
  -d username=subscriber@telco.local -d password=subscriber | jq -r .access_token)
curl -H "Host: telco.local" -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/v1/tariffs
```

---

## 7. Scaling (HPA)

HPA is **enabled by default** for the 10 domain services + the gateway
(min 2 / max 5 / 75% CPU; config-server and discovery-server are singletons and
do not autoscale). Requires metrics-server (Section 2.3).

```sh
kubectl -n telco get hpa                         # live CPU% / replicas per service
kubectl -n telco describe hpa api-gateway        # scale events + behavior policy
```

Verified live (15.3): under load a service scales out 1->max and, once utilization
falls below target, scales back in to min after the 60s stabilization window
(1 pod / 30s). Manual scaling is possible only where autoscaling is off
(`kubectl -n telco scale deploy/<svc> --replicas=N`); for HPA-managed services,
tune the HPA (`kubectl -n telco edit hpa/<svc>`) instead.

Availability during disruption is protected by a PodDisruptionBudget
(`minAvailable: 1`) plus `maxUnavailable: 0` rollouts - verified live: a node
drain / eviction cannot take the last replica, and a rolling deploy keeps the
service serving throughout.

---

## 8. Rollback

Full procedure and live evidence: [`deploy/ROLLBACK.md`](ROLLBACK.md). Quick form:

```sh
helm -n telco history <service>        # find the last good REVISION
helm -n telco rollback <service>       # to the previous revision (or `... <REV>`)
kubectl -n telco rollout status deploy/<service> --timeout=180s
```

A bad deploy is detected by failing readiness (a broken image leaves the new pod
NotReady while `maxUnavailable: 0` keeps the old pods serving) and by the smoke
test (Section 9). In CI, smoke failure triggers rollback automatically.

---

## 9. Post-deploy smoke test

```sh
# Against the live stack (Kind: gateway on localhost:18080, Keycloak port-forwarded
# automatically). Requires curl + jq.
bash deploy/smoke/smoke-test.sh
# Override targets/services as needed, e.g.:
#   GATEWAY_URL=http://telco.local READINESS_SERVICES="config-server api-gateway ..." bash deploy/smoke/smoke-test.sh
```

It checks gateway health via the Ingress, key-service readiness, obtains a real
Keycloak token, and performs one authenticated read through the gateway. It exits
non-zero on any failure (which, in CI, triggers rollback).

---

## 10. Observability

The `dependencies` chart deploys the full stack (ADR-012). All are ClusterIP;
port-forward to reach a UI:

```sh
kubectl -n telco port-forward svc/grafana    3000:3000   # dashboards (platform-overview, kafka-billing, circuit-breakers)
kubectl -n telco port-forward svc/prometheus 9090:9090   # metrics + alerts
# Traces (Tempo) and logs (Loki) are wired as Grafana datasources; services push
# OTLP to otel-collector:4318 and logs to loki:3100 (the `docker` profile addresses).
```

---

## 11. Teardown

```sh
helm -n telco list -q | xargs -r -n1 helm -n telco uninstall   # remove all releases
helm -n telco uninstall telco-deps                             # remove dependencies
helm -n linkerd uninstall linkerd-control-plane                # remove Linkerd control plane (19.1)
helm -n linkerd uninstall linkerd-crds                         # remove Linkerd CRDs
kind delete cluster --name telco                               # destroy the cluster
```

---

## 12. Known follow-ups (tracked, non-blocking for the pipeline)

These are tracked in `docs/tasks/STATUS.md` / `docs/tasks/todo.md` and are not
deployment-artifact defects:

1. **schema-registry** exits 1 at the Confluent "Configuring" stage; several
   domain services depend on it, so the first fully-green 13-service boot needs
   this fixed (CI Kind run).
2. **product-catalog** returns 500 on `GET /api/v1/tariffs` in-cluster (unhandled,
   `@Cacheable` path) - domain-engineer follow-up. The full happy-path smoke read
   goes green once this is fixed.
3. **Actuator probe security**: services permit only `/actuator/health` (not the
   `/actuator/health/**` sub-groups), so probes use `/actuator/health`. Permit the
   sub-groups in the 10 `SecurityConfig`s to restore proper liveness/readiness
   separation (security agent).
4. **CI image coverage**: CI builds only *changed* service images per commit, so a
   full-stack deploy at a per-commit sha would `ImagePullBackOff` unchanged
   services - use the `deploy.yml` `workflow_dispatch` `image_tag=latest` override
   (or a platform/config/reactor-pom change, which rebuilds all 13).
5. **`linkerd-viz` deferred, not shipped by 19.1**: `linkerd check` and
   `linkerd check --proxy` (both CLI-only, no extra in-cluster component) are
   sufficient to prove control-plane health and mTLS identity for this
   feature's scope. `linkerd-viz` (its own Prometheus + web dashboard +
   `linkerd viz stat`) is deliberately left for whichever of 19.2/19.5 first
   needs live per-service traffic metrics (e.g. proving an unauthorized-caller
   request is actually rejected, not just that certs match) - installing a
   second Prometheus alongside the `dependencies` chart's existing one without
   a concrete consumer yet would be premature for this feature.

---

## 13. Vault Kubernetes auth method and per-service policies (18.2, ADR-025)

Appended here (a new section, rather than inserted into Section 3, to avoid
renumbering every downstream cross-reference to this document from later
sprints' task specs). Prerequisite: Vault installed, initialized, and
**unsealed** (Sections 2.4 and 3). Everything below is scripted in
[`deploy/helm/vault/bootstrap-k8s-auth.sh`](helm/vault/bootstrap-k8s-auth.sh)
(idempotent - safe to re-run) using the per-service policy files in
[`deploy/helm/vault/policies/`](helm/vault/policies/); the commands are shown
inline here for reference.

### 13.1 Enable and configure the Kubernetes auth method (18.2.1)

Vault's own `ServiceAccount` (`vault`, in the `telco` namespace) is granted
the `system:auth-delegator` `ClusterRole` by
[`deploy/helm/vault/templates/auth-delegator-clusterrolebinding.yaml`](helm/vault/templates/auth-delegator-clusterrolebinding.yaml)
(installed automatically as part of the `vault` Helm release, Section 2.5) -
this lets Vault call the Kubernetes TokenReview API to validate the JWTs
other pods present when they log in. Run the config write from inside the
Vault pod itself: its own projected `ServiceAccount` token/CA (standard
downward-API paths) are exactly the token-reviewer credentials this needs.

```sh
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> \
  vault auth enable kubernetes

kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> sh -c '
  vault write auth/kubernetes/config \
    kubernetes_host="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}" \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
'

# Verify: kubernetes/ listed, config shows the in-cluster API host.
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> vault auth list
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> vault read auth/kubernetes/config
```

### 13.2 Enable KV v2 and write one policy per service (18.2.2)

The KV v2 engine is enabled once at `secret/`. Every one of the 13 services
consumes at least one secret today (`deploy/helm/README.md` "Config / Secret
model": `EUREKA_PASSWORD` is read by all 13), so all 13 get a policy, each
scoped to exactly that service's own path and nothing else:

```sh
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> \
  vault secrets enable -path=secret kv-v2

kubectl -n telco exec -i vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> \
  vault policy write customer-service - < deploy/helm/vault/policies/customer-service.hcl
# ... repeated per deploy/helm/vault/policies/<service>.hcl (bootstrap-k8s-auth.sh loops all 13)
```

Service -> Vault path -> policy name mapping (every service's policy is
identical in shape - `read`+`list` on `secret/data/<service>/*` and
`secret/metadata/<service>/*` - only the path prefix differs):

| Service | Vault KV path (read/list) | Policy name | Key secrets that will live there (18.4/18.5) |
| --- | --- | --- | --- |
| api-gateway | `secret/data/api-gateway/*` | `api-gateway` | `EUREKA_PASSWORD`, `REDIS_PASSWORD` |
| billing-service | `secret/data/billing-service/*` | `billing-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| config-server | `secret/data/config-server/*` | `config-server` | `EUREKA_PASSWORD`, `ENCRYPT_KEY`, `CONFIG_SERVER_PASSWORD` |
| customer-service | `secret/data/customer-service/*` | `customer-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, `CUSTOMER_AES_KEY`, DB credentials |
| discovery-server | `secret/data/discovery-server/*` | `discovery-server` | `EUREKA_PASSWORD` |
| identity-service | `secret/data/identity-service/*` | `identity-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| notification-service | `secret/data/notification-service/*` | `notification-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| order-service | `secret/data/order-service/*` | `order-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| payment-service | `secret/data/payment-service/*` | `payment-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| product-catalog-service | `secret/data/product-catalog-service/*` | `product-catalog-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| subscription-service | `secret/data/subscription-service/*` | `subscription-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| ticket-service | `secret/data/ticket-service/*` | `ticket-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |
| usage-service | `secret/data/usage-service/*` | `usage-service` | `EUREKA_PASSWORD`, `CONFIG_SERVER_PASSWORD`, `REDIS_PASSWORD`, DB credentials |

No secret values are written under these paths by this feature (18.2 is
auth/policy only) - population is Features 18.4 (core secrets) and 18.5
(per-service DB credentials).

### 13.3 Bind each service's ServiceAccount to its policy (18.2.3)

Each service's `ServiceAccount` name already equals its `serviceName`
(`deploy/helm/telco-service/templates/serviceaccount.yaml`,
`telco-service.serviceAccountName` - no service overrides `serviceAccount.name`
in `deploy/helm/values/*.yaml`), in the `telco` namespace. One
`auth/kubernetes/role/<service>` per service binds that identity to the
matching policy from 13.2:

```sh
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> \
  vault write auth/kubernetes/role/customer-service \
    bound_service_account_names=customer-service \
    bound_service_account_namespaces=telco \
    policies=customer-service \
    ttl=15m
# ... repeated per service (bootstrap-k8s-auth.sh loops all 13)

# Verify:
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=<root-or-privileged-token> \
  vault read auth/kubernetes/role/customer-service
```

### 13.4 Live login test from a real pod

From inside a pod running under the `customer-service` `ServiceAccount`
(any pod using that identity - it does not need to be a healthy service,
only `Running` with the projected token present):

```sh
kubectl -n telco exec -it deploy/customer-service -- sh -c '
  JWT=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
  curl -s --request POST http://vault:8200/v1/auth/kubernetes/login \
    -d "{\"jwt\": \"$JWT\", \"role\": \"customer-service\"}"
'
# -> returns a client token whose auth.policies includes "customer-service"
#    (and "default"), scoped to secret/data/customer-service/* only.
```

Confirm the returned token can read `secret/data/customer-service/*` and is
**denied** on another service's path (e.g. `secret/data/billing-service/*`,
HTTP 403) using that token as `X-Vault-Token`.

---

## 14. Seed the five core credentials into Vault KV v2 (18.4.1, ADR-025 Section 2)

Prerequisite: Section 13 (Kubernetes auth + per-service policies) has run, so
the `secret/` KV v2 engine is enabled and each service's policy already
scopes it to its own path. This step only writes *values*.

```sh
VAULT_ROOT_TOKEN=<root-or-privileged-token> deploy/helm/vault/seed-secrets.sh
```

This generates, once per environment (fresh random values, never reused
across environments and never committed):

- `ENCRYPT_KEY` - `openssl rand -hex 32` (64 hex chars), written to
  `secret/config-server/encrypt-key`.
- `CUSTOMER_AES_KEY` - `openssl rand -base64 32` (decodes to exactly 32 bytes,
  satisfying `AesKeyProvider`'s AES-256 validation, NFR-06), written to
  `secret/customer-service/aes-key`.
- `CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD` - one value
  each (ADR-025 Section 2: these are shared credentials, not per-service
  secrets - config-server's own basic-auth password, Eureka's basic-auth
  password, and the single Redis password must be identical wherever they are
  read), written to every consuming service's own `secret/<service>/app`
  path so Vault policy still scopes *who can read it* per service even though
  the *value* is shared.

Verify (values are real once seeded - never paste the output into a commit,
log, or chat):

```sh
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault kv get secret/config-server/encrypt-key
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault kv get secret/customer-service/aes-key
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault kv get secret/customer-service/app
```

Then install/upgrade services with `--set vault.enabled=true` (Section 4,
`deploy/helm/README.md` "Config / Secret model") and confirm the pod's
`envFrom`-sourced environment carries the seeded value, not the local-dev
placeholder from `deploy/helm/values/<service>.yaml`:

```sh
kubectl -n telco exec deploy/config-server -- env | grep -E 'ENCRYPT_KEY|EUREKA_PASSWORD|CONFIG_SERVER_PASSWORD'
kubectl -n telco exec deploy/customer-service -- env | grep -E 'CUSTOMER_AES_KEY|EUREKA_PASSWORD|CONFIG_SERVER_PASSWORD|REDIS_PASSWORD'
```

---

## 15. Seed per-service DB credentials into Vault KV v2 and retire the docker-profile plaintext DB block (18.5, ADR-025 Section 2/4)

Closes the gap identified in ADR-025's Context section: per-service PostgreSQL credentials were not
secreted at all before this feature (they were baked in plaintext into
`microservices/configs/<service>/application-docker.yml`, e.g. `customer`/`customer`).

Prerequisite: Section 13 has run (per-service Vault policies already scope `secret/data/<service>/*`,
which the `db-credentials` path falls under - **no new policy is needed**), and the `postgres-0` pod
(`deploy/helm/dependencies`) is running.

### 15.1 Generate and write per-service DB credentials (18.5.1)

The same script from Section 14 now also handles DB credentials:

```sh
VAULT_ROOT_TOKEN=<root-or-privileged-token> deploy/helm/vault/seed-secrets.sh
```

For every PostgreSQL-backed service (`docs/architecture/service-catalog.md` Section 5 - all services
except `api-gateway`, `discovery-server`, `config-server`, plus `notification-service`'s PostgreSQL
outbox database), this:

- Generates one fresh, real, random password per service (`openssl rand -base64 24`).
- Keeps the **username** as the service's existing Postgres role name (`deploy/helm/dependencies/files/postgres/01-create-databases.sql`
  already gives each service its own non-shared role and owns that service's database, per ADR-006 -
  e.g. `customer` owns `customer_db`; renaming the role would require reassigning database ownership
  for no security benefit this feature is scoped to deliver, so it is not done here).
- Writes `username`/`password` to `secret/<service>/db-credentials`.
- **Rotates the live Postgres role's password to match** (`ALTER USER <role> WITH PASSWORD '...'`
  against `postgres-0`), so the credential in Vault is not just real-looking but actually the
  credential the database will accept - the committed `<service>`/`<service>` dev-default password no
  longer authenticates against the live database once this has run.

Verify:

```sh
kubectl -n telco exec -it vault-0 -- env VAULT_TOKEN=$VAULT_ROOT_TOKEN vault kv get secret/customer-service/db-credentials
# username: customer (unchanged, see above); password: a fresh base64 string, NOT "customer"
```

### 15.2 In-cluster profile: `dev,k8s` replaces `dev,docker` for PostgreSQL-backed services

Audited every PostgreSQL-backed service's `application-prod.yml` against its `application-docker.yml`
(ADR-025 Section 4's deferred audit). Finding: for **every** one of the 10 services, `prod` on its own
is *not* safe to activate in-cluster as-is - it only externalizes DB (and, for `customer-service`/
`billing-service`, MinIO) credentials, but drops the `docker` profile's other in-cluster-required
overrides entirely: Kafka `bootstrap-servers` (falls back to `application.yml`'s `localhost:9092`,
unreachable from a pod), the Keycloak JWKS URI (falls back to `localhost:8085`), and (for
`order-service`, `usage-service`, `billing-service`, `subscription-service`) inter-service `telco.clients`
URLs. Activating `prod` alone in-cluster would silently break Kafka connectivity, JWT validation, and
service-to-service calls for all 10 services - reintroducing exactly the class of "docker override
missing" bug already found and fixed twice in Sprint 15/17 (see Section 12).

Decision: every PostgreSQL-backed service gets a new `microservices/configs/<service>/application-k8s.yml`
- functionally `application-docker.yml` with the DB `username`/`password` fields replaced by
`${<SERVICE>_DB_USER}`/`${<SERVICE>_DB_PASSWORD}` placeholders (matching `application-prod.yml`'s naming
convention) - and every affected `deploy/helm/values/<service>.yaml`'s `SPRING_PROFILES_ACTIVE` changed
from `dev,docker` to `dev,k8s`. The jdbc URL's host/port/database-name are not secret and stay
hardcoded (unchanged from `application-docker.yml`) rather than moving to Vault or a ConfigMap entry,
per ADR-025 Section 2's secret-vs-non-secret distinction. `prod` itself is untouched and remains
available for a genuine future prod environment with its own MinIO/SMTP/PSP/JWKS endpoints; no service
was found where `prod` was safe to reuse in-cluster as-is.

No non-PostgreSQL-backed service (`api-gateway`, `discovery-server`, `config-server`) is touched by
this change.

### 15.3 CSI sync and live verification (18.5.3)

`deploy/helm/telco-service/templates/secretproviderclass.yaml` already iterates
`.Values.vault.secretKeys` generically (18.3.2) - no template change was needed. Each affected
service's `deploy/helm/values/<service>.yaml` gained two more `vault.secretKeys` entries pointing at
`secret/<service>/db-credentials`'s `username`/`password` fields, materialized into the existing
`<service>-secret` object under the exact key names `application-k8s.yml` expects (e.g.
`CUSTOMER_DB_USER`/`CUSTOMER_DB_PASSWORD` for customer-service).

Live-verify (after Sections 13-15.1 have run and the chart is installed/upgraded with
`--set vault.enabled=true`):

```sh
kubectl -n telco exec deploy/billing-service -- env | grep -E 'BILLING_DB_USER|BILLING_DB_PASSWORD|SPRING_PROFILES_ACTIVE'
# BILLING_DB_USER=billing (unchanged role name); BILLING_DB_PASSWORD=<fresh Vault-generated value,
# NOT "billing">; SPRING_PROFILES_ACTIVE=dev,k8s (NOT dev,docker)
```

**Important finding, broader than Feature 18.4's note, discovered live-verifying this feature**: no
PostgreSQL-backed service reaches full pod `Ready` in this cluster today, and `SPRING_PROFILES_ACTIVE`
switching from `dev,docker` to `dev,k8s` does **not** fix it - this is the SAME pre-existing,
Vault-unrelated config-server bug 18.4 flagged for customer-service
(`FailedToConstructEnvironmentException: ... found duplicate key spring`), but live testing during 18.5
shows it is not specific to the `docker` profile or to customer-service: `config-server`'s
`/{service}/dev` and `/{service}/dev,k8s` HTTP endpoints both return `500` for every service that has
its own `application-dev.yml` (all 10 PostgreSQL-backed services, plus `api-gateway`, confirmed live for
`billing-service`, `customer-service`, `identity-service`, `ticket-service`, `order-service`), because
the merge conflict is between the root `microservices/configs/application-dev.yml` and the service's own
`application-dev.yml` - both declare a top-level `spring:` key - independent of which second profile is
requested. **No profile rename sidesteps this**; it is still exactly the bug flagged for
domain-engineer/event-integration in 18.4, now confirmed platform-wide rather than customer-service-only.
Not fixed here per this feature's explicit scope boundary (config-server's native-repository merge
behavior is Java-application-adjacent, not a `deploy/` concern).

Because no service's app process reaches a point where it opens a live `DataSource` connection (config
fetch fails before `spring.datasource.*` is ever resolved), full-`Ready`-implies-DB-connectivity is not
achievable for this live-verification round. Instead, DB credential delivery was proven directly,
bypassing the blocked app boot: the pod's CSI-synced `<SERVICE>_DB_USER`/`<SERVICE>_DB_PASSWORD`
env vars were read (`kubectl exec ... -- env`), confirmed to match Vault's `secret/<service>/db-credentials`
exactly, and then used in a direct `psql` connection **from the `postgres-0` pod to its own Service IP**
(not `localhost`, which hits Postgres's `trust`-auth loopback rule and would prove nothing) using each
service's exact injected credential - this is the same `scram-sha-256`-authenticated network path an
application pod would use. Both `billing-service` and `customer-service` were proven this way:

```sh
POD_IP=$(kubectl -n telco get pod postgres-0 -o jsonpath='{.status.podIP}')
kubectl -n telco exec -i postgres-0 -- env PGPASSWORD=<value from Vault kv get> \
  psql -h "$POD_IP" -U billing -d billing_db -c "select current_user;"
# succeeds: current_user = billing

kubectl -n telco exec -i postgres-0 -- env PGPASSWORD=billing \
  psql -h "$POD_IP" -U billing -d billing_db -c "select 1;"
# FATAL: password authentication failed for user "billing" - the old committed dev-default no
# longer authenticates, proving seed-secrets.sh's ALTER USER rotation is real, not just Vault-side
```

See `docs/tasks/STATUS.md`'s 18.5 entry for the full, honest accounting of what was and was not
verified.

Full end-to-end run and outcome: [`docs/tasks/sprint-18-secret-management/README.md`](../docs/tasks/sprint-18-secret-management/README.md).
