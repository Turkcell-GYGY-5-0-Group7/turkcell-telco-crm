# Telco CRM - Kubernetes / Helm deployment

This directory holds the Helm charts that deploy the Telco CRM platform to a
Kubernetes cluster (developed and statically validated against a Kind cluster).

```
deploy/helm/
  dependencies/          # in-cluster stateful deps (15.2.3): postgres, redis,
                         # mongo, kafka, schema-registry, kafka-connect, minio,
                         # keycloak, otel-collector, tempo, loki, prometheus, grafana
  vault/                 # HashiCorp Vault (18.1, ADR-025): standalone server,
                         # Integrated Storage (Raft), injector disabled, Vault
                         # CSI provider DaemonSet enabled (18.3, `vault.csi`) -
                         # wraps the official hashicorp/vault chart as a
                         # dependency. Kubernetes auth + per-service policies/
                         # roles (18.2) and CSI-synced secrets (18.3) both
                         # consume it now.
  csi-driver/             # Secrets Store CSI Driver (18.3, ADR-025 Section 1):
                         # wraps the upstream kubernetes-sigs
                         # `secrets-store-csi-driver` chart as a dependency,
                         # same vendored pattern as vault/. The Vault CSI
                         # provider half ships inside the `vault` chart itself
                         # (`vault.csi.enabled`, see vault/values.yaml) rather
                         # than as a second chart here - see
                         # deploy/helm/csi-driver/Chart.yaml for why.
  linkerd-crds/           # Linkerd CRDs (19.1.1, ADR-026): wraps the official
                         # `linkerd-crds` chart, installed before the control
                         # plane per Linkerd's own two-chart install model.
  linkerd-control-plane/  # Linkerd control plane (19.1.1, ADR-026): wraps the
                         # official `linkerd-control-plane` chart -
                         # linkerd-identity (mTLS workload identity, Linkerd's
                         # self-managed trust anchor, NOT cert-manager/Vault
                         # PKI), linkerd-destination, linkerd-proxy-injector
                         # (automatic sidecar injection). Installs into its
                         # own `linkerd` namespace, not `telco`.
                         # `generate-trust-anchor.sh` in this directory
                         # produces the root CA / issuer cert at install time
                         # - never committed, same handling as Vault's unseal
                         # keys (deploy/RUNBOOK.md Section 3).
  telco-service/         # ONE reusable chart that renders a single Spring Boot service
  values/                # one values file per service (13 files)
  README.md              # this file
```

## Design: one chart, one release per service

`telco-service/` is a single reusable chart. Each of the 13 services is a
separate Helm **release** that layers its own `values/<service>.yaml` on top of
the chart defaults. Separate releases mean each service can be upgraded and
**rolled back independently** (`helm rollback <service>`, Sprint 15.4.2).

The 13 services and ports:

| Tier   | Services |
| ------ | -------- |
| Infra  | api-gateway (8080), discovery-server (8761), config-server (8888) |
| Domain | identity (9001), customer (9002), product-catalog (9003), order (9004), subscription (9005), usage (9006), billing (9007), payment (9008), notification (9009), ticket (9010) |

Each Service object is named identically to its compose service name, so the
in-cluster short-name DNS (`config-server`, `discovery-server`,
`customer-service`, ...) matches what the Spring `docker` profile and the
gateway `lb://` routes expect. Services run with `SPRING_PROFILES_ACTIVE=dev,docker`;
the `docker` profile's DNS names (`postgres`, `kafka`, `redis`, `keycloak`,
`discovery-server`, `config-server`, `minio`, `mongo`) now resolve in-cluster
because the dependency chart creates Services under those exact names. No `k8s`
profile is required - there is no in-cluster config delta over `docker`.

## Install order

```sh
# 1. Dependencies (creates the `telco` namespace, annotated
#    `linkerd.io/inject: enabled` per 19.1.2/ADR-026 - see `linkerdInject` in
#    deploy/helm/dependencies/values.yaml - plus all stateful backends).
helm install telco-deps deploy/helm/dependencies --namespace telco --create-namespace

# 2. Linkerd (19.1.1, ADR-026 Section 1) - the service mesh control plane,
#    installed into its own `linkerd` namespace (NOT `telco`), CRDs first per
#    Linkerd's official two-chart model. Self-managed trust anchor (no
#    cert-manager, no Vault PKI) - generate the root CA/issuer cert with
#    generate-trust-anchor.sh, never committed.
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
linkerd check   # all control-plane checks must be green before proceeding

# 3. Vault (18.1, ADR-025) - the platform's secret store. Installed alongside
#    the dependencies, ahead of any service that will eventually consume
#    Vault-sourced secrets. `vault.csi.enabled: true` (18.3) also deploys the
#    Vault CSI provider DaemonSet as part of this same release - see
#    deploy/helm/vault/values.yaml.
helm dependency update deploy/helm/vault
helm install vault deploy/helm/vault -n telco
# NOT `rollout status` (the chart uses updateStrategyType: OnDelete, which
# rollout status refuses to track) and NOT `wait --for=condition=ready` (the
# readiness probe runs `vault status`, which fails while sealed - the pod
# only becomes Ready after the unseal procedure below).
kubectl -n telco wait --for=condition=Initialized pod -l app.kubernetes.io/name=vault --timeout=180s
# Then run the one-time init/unseal procedure - see deploy/RUNBOOK.md
# "Vault initialization and unseal" - and the Kubernetes auth method /
# per-service policy bootstrap (deploy/helm/vault/bootstrap-k8s-auth.sh, 18.2)
# before any service is expected to authenticate to Vault.

# 4. Secrets Store CSI Driver (18.3, ADR-025 Section 1) - the runtime
#    component that performs the SecretProviderClass -> Kubernetes Secret
#    sync (18.3.2/18.3.3). Installed after Vault (its CSI provider half is
#    already up via step 3), before any service release that sets
#    vault.enabled=true.
helm dependency update deploy/helm/csi-driver
helm install csi-driver deploy/helm/csi-driver -n telco
kubectl -n telco rollout status daemonset/csi-driver-secrets-store-csi-driver --timeout=180s
kubectl -n telco rollout status daemonset/vault-csi-provider --timeout=180s
kubectl get csidriver secrets-store.csi.k8s.io

# 5. Infra services first (config-server before the rest so they can bootstrap).
#    Each pod is automatically proxy-injected (2 containers: app + linkerd-proxy)
#    because of step 1's namespace annotation - no chart change needed here.
helm install config-server    deploy/helm/telco-service -n telco -f deploy/helm/values/config-server.yaml    --set image.owner=<OWNER>
helm install discovery-server deploy/helm/telco-service -n telco -f deploy/helm/values/discovery-server.yaml --set image.owner=<OWNER>
helm install api-gateway      deploy/helm/telco-service -n telco -f deploy/helm/values/api-gateway.yaml      --set image.owner=<OWNER>

# 6. Domain services.
for s in identity-service customer-service product-catalog-service order-service \
         subscription-service usage-service billing-service payment-service \
         notification-service ticket-service; do
  helm install "$s" deploy/helm/telco-service -n telco -f "deploy/helm/values/$s.yaml" --set image.owner=<OWNER>
done
```

Images are `ghcr.io/<owner>/telco-<service>:<tag>` (built in 15.1). Set the
registry owner with `--set image.owner=<OWNER>` (or edit the values file); the
tag defaults to `latest` (`--set image.tag=<sha>` to pin).

Ingress (api-gateway only) is `nginx`, host `telco.local`, path `/`. Install
ingress-nginx and point `telco.local` at the ingress controller to reach the
gateway from outside the cluster. All other services are ClusterIP-only.

## Config / Secret model (15.2.2, ADR-010, NFR-06, ADR-025 Section 1)

Each service's environment (from its `infra/docker/compose.yml` block) is split:

* **Non-secret** env (`config:` map -> per-service **ConfigMap**
  `<service>-config`): config-server URI, profile, usernames, hosts, ports,
  URIs, feature flags. Projected via `envFrom.configMapRef`. **Unchanged by
  Feature 18.3** - Vault is a secret store, not a config store.
* **Secret** env -> per-service **Secret** `<service>-secret`, projected via
  `envFrom.secretRef` exactly as before. **How that Secret is populated now
  has two modes, gated by `vault.enabled` (default `false`)** - the Secret's
  name and key names are identical either way, so `envFrom.secretRef` in
  `templates/deployment.yaml` never changes.

Secret keys (consistent across services and with the `dependencies` chart's
`REDIS_PASSWORD`):

| Secret key             | Used by | Purpose |
| ---------------------- | ------- | ------- |
| `CONFIG_SERVER_PASSWORD` | all except discovery-server | config-server basic-auth |
| `EUREKA_PASSWORD`        | all | Eureka basic-auth |
| `REDIS_PASSWORD`         | api-gateway + 10 domain | Redis auth (matches redis-credentials) |
| `ENCRYPT_KEY`            | config-server | Spring Cloud Config symmetric encrypt key |
| `CUSTOMER_AES_KEY`       | customer-service | AES-256 PII-at-rest key (NFR-06) |

### `vault.enabled: true` (Vault-backed, PRIMARY path for any non-local environment - Feature 18.3/18.4, ADR-025 Section 1)

`templates/secret.yaml` renders nothing; instead:

1. `templates/secretproviderclass.yaml` renders a `SecretProviderClass`
   (`spec.provider: vault`, `roleName` = the service's Vault Kubernetes-auth
   role from 18.2.3, `objects` = the Vault KV v2 paths/fields listed in that
   service's `vault.secretKeys` values override) plus a `secretObjects` block
   naming the target Secret `<service>-secret` with the same key names as the
   static model above.
2. `templates/deployment.yaml` mounts that `SecretProviderClass` as a
   **read-only CSI volume** on the pod (`driver:
   secrets-store.csi.k8s.io`), which is what actually triggers the sync on
   pod start.
3. The Secrets Store CSI Driver + Vault CSI provider (`deploy/helm/csi-driver/`
   + `deploy/helm/vault`'s `vault.csi.enabled`, both installed ahead of any
   service per the install order above) authenticate to Vault using that
   pod's own `ServiceAccount` via the Kubernetes auth method (18.2), read
   exactly the KV v2 paths the service's Vault policy permits
   (`secret/data/<service>/*`, nothing else), and materialize them as an
   ordinary Kubernetes `Secret` named `<service>-secret` with the exact same
   keys the static model used.
4. `envFrom.secretRef` in `templates/deployment.yaml` is **byte-for-byte
   unchanged** from the `vault.enabled: false` render - it targets
   `<service>-secret` either way; only that Secret's *source* differs.

Enable per-service at install time (requires Vault initialized/unsealed,
18.2's auth/policies bootstrapped, and `csi-driver` installed - see
`deploy/RUNBOOK.md` Sections 2-3 and 13):

```sh
helm upgrade --install customer-service deploy/helm/telco-service -n telco \
  -f deploy/helm/values/customer-service.yaml --set vault.enabled=true
```

Each service's `vault.secretKeys` override (added to every
`values/<service>.yaml`) declares which of its secret keys sync from Vault and
which KV v2 path/field each one reads - see `deploy/helm/values/customer-service.yaml`
for the shape (a shared `<service>/app` path for the common credentials, plus
a dedicated path for `ENCRYPT_KEY`/`CUSTOMER_AES_KEY` matching ADR-025 Section 2).
The five core credentials are populated into Vault by `deploy/helm/vault/seed-secrets.sh`
(Feature 18.4.1, `deploy/RUNBOOK.md` Section 14); per-service DB credentials
remain Feature 18.5.

### `vault.enabled: false` (LOCAL-DEV-ONLY - not for staging/production)

`templates/secret.yaml` renders a static Secret straight from the `secrets:`
values map. The values in `values/<service>.yaml` under `secrets:` are
obvious, non-random **local-dev-only placeholders** (`config`/`eureka`/`telco`
credentials, a repeating-hex-pattern `ENCRYPT_KEY`, a repeating-byte-pattern
`CUSTOMER_AES_KEY`) that exist only so a from-scratch local Kind or compose
environment boots without standing up Vault first. **No real/production
secret has ever been, or may ever be, committed here** - as of 18.4.2 none of
these values are even shaped like plausible real key material. Do not use
this mode, or override these values at install time, for anything other than
local development; every staging/production install MUST use
`vault.enabled=true` above instead.

### config-server special case

config-server IS the config source and cannot import from itself (ADR-010). Its
image bakes `microservices/configs` into `/configs` and runs the `native`
backend (`spring.profiles.active=native` lives in the image, so no
`SPRING_PROFILES_ACTIVE` is set for it). We deliberately do **not** mount the
nested `microservices/configs` tree as a ConfigMap - a ConfigMap is a flat
key/value map and cannot represent the per-service subdirectories - so the
baked configs are authoritative. config-server only needs `ENCRYPT_KEY` (Secret,
mandatory), its basic-auth `CONFIG_SERVER_USER`/`CONFIG_SERVER_PASSWORD`, and the
same host/credential env the compose block carries (it substitutes `${...}`
placeholders in the YAML it serves using its own environment).

## Probes

All three probes (startup, liveness, readiness) target `/actuator/health`, NOT
the `/actuator/health/liveness` and `/actuator/health/readiness` sub-groups.
Reason (found in live Kind verification, 15.4): every service's `SecurityConfig`
permits exactly `/actuator/health` + `/actuator/info` but NOT the sub-group
paths, so probing the sub-groups returns 401 and the kubelet kills the pod
(liveness) - crash-looping every domain service. `/actuator/health` is permitted
and returns overall `UP`. A generous `startupProbe` (up to ~5 min) absorbs the
slow JVM cold start so a booting pod is never killed. Paths and an optional
basic-auth header are overridable per service (`probes.*`).

> **Follow-up (security agent):** permit `/actuator/health/**` in the 10 service
> `SecurityConfig`s, then repoint liveness/readiness to the dedicated
> `/actuator/health/liveness` and `/actuator/health/readiness` groups to restore
> proper liveness/readiness separation.

* **config-server** exposes only `health,info` and its `/actuator/health` is
  behind basic auth, so its three probes target `/actuator/health` and send a
  dev-default `Authorization: Basic` header (override `probes.httpHeaders` when
  the credentials change).
* **discovery-server** exposes only `health,info` (health is unauthenticated),
  so its probes also target `/actuator/health` rather than the sub-groups.

## HPA / PDB

`autoscaling.enabled` and `pdb.enabled` ship **enabled by default** (15.3):
HPA `minReplicas: 2 / maxReplicas: 5 / targetCPUUtilizationPercentage: 75` with a
`behavior` block (fast scaleUp, 60s scaleDown stabilization, 1 pod/30s), and a
PodDisruptionBudget `minAvailable: 1`. The two singleton infra services -
**config-server** and **discovery-server** - override both **off**
(`autoscaling.enabled: false`, `pdb.enabled: false`, `replicaCount: 1`) because a
PDB on a single replica would block node drains. HPA requires **metrics-server**
in the cluster. The Deployment uses a `maxUnavailable: 0` RollingUpdate so
upgrades preserve availability (NFR-04). Both scale-out/scale-in and PDB
enforcement were live-verified on Kind (15.3).

**Vault** (18.1, ADR-025) is a third singleton, same reasoning: single-replica
StatefulSet, no PodDisruptionBudget (`server.ha.disruptionBudget.enabled: false`
in `deploy/helm/vault/values.yaml`) and not targeted by any HPA - it is not part
of the `telco-service` chart's autoscaling machinery at all.

**CSI driver** (18.3, ADR-025) is likewise a DaemonSet, not part of this
autoscaling machinery - see `deploy/helm/csi-driver/`.

## Validate (no cluster needed)

```sh
# Both Secret-source modes (18.3): vault.enabled defaults to false, so the
# first loop is the Sprint 15 static-Secret render; the second is the
# CSI-synced render (SecretProviderClass + CSI volume, no static Secret).
for s in $(ls deploy/helm/values | sed 's/.yaml//'); do
  helm lint     deploy/helm/telco-service -f "deploy/helm/values/$s.yaml"
  helm template "$s" deploy/helm/telco-service -f "deploy/helm/values/$s.yaml" -n telco
done
for s in $(ls deploy/helm/values | sed 's/.yaml//'); do
  helm lint     deploy/helm/telco-service -f "deploy/helm/values/$s.yaml" --set vault.enabled=true
  helm template "$s" deploy/helm/telco-service -f "deploy/helm/values/$s.yaml" -n telco --set vault.enabled=true
done

helm lint deploy/helm/csi-driver
helm template csi-driver deploy/helm/csi-driver -n telco

# Linkerd (19.1.1): linkerd-crds needs no special values. linkerd-control-plane
# requires a real (locally-generated, not committed) trust anchor/issuer cert
# for helm template/lint to render the identity secret meaningfully - generate
# a throwaway one for local validation only.
helm lint deploy/helm/linkerd-crds
helm template linkerd-crds deploy/helm/linkerd-crds -n linkerd

bash deploy/helm/linkerd-control-plane/generate-trust-anchor.sh /tmp/linkerd-trust-validate
helm lint deploy/helm/linkerd-control-plane \
  --set-file linkerd-control-plane.identityTrustAnchorsPEM=/tmp/linkerd-trust-validate/ca.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.crtPEM=/tmp/linkerd-trust-validate/issuer.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.keyPEM=/tmp/linkerd-trust-validate/issuer.key
helm template linkerd-control-plane deploy/helm/linkerd-control-plane -n linkerd \
  --set-file linkerd-control-plane.identityTrustAnchorsPEM=/tmp/linkerd-trust-validate/ca.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.crtPEM=/tmp/linkerd-trust-validate/issuer.crt \
  --set-file linkerd-control-plane.identity.issuer.tls.keyPEM=/tmp/linkerd-trust-validate/issuer.key
```
