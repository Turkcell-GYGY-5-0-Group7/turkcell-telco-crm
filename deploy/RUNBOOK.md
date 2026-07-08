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
instead build images locally and `kind load` them (Section 4.2).

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
```

For a managed cluster (EKS/GKE/AKS), skip 2.1; use the provider's ingress and a
real metrics-server, and point a DNS name at the ingress instead of `telco.local`.

---

## 3. Configuration and secrets

Full model in [`deploy/helm/README.md`](helm/README.md) ("Config / Secret model").
Summary:

- Per-service **ConfigMap** (`<service>-config`) carries non-secret env; per-service
  **Secret** (`<service>-secret`) carries credentials/keys (`ENCRYPT_KEY`,
  `*_PASSWORD`, `CUSTOMER_AES_KEY`). Both projected via `envFrom`.
- The values under `secrets:` in `deploy/helm/values/*.yaml` are **DEV-ONLY**
  defaults. **No production secret is committed.** For a real cluster, override at
  install (`--set-file secrets.ENCRYPT_KEY=./encrypt.key`, `--set secrets.X=...`)
  or supply a pre-created / external Secret whose keys match those names.
- Dependency credentials (postgres/redis/minio/keycloak/grafana) live in the
  `dependencies` chart's Secrets - override the same way for non-dev.
- config-server serves the bulk of runtime config from its baked `/configs`
  (ADR-010); it resolves `${...}` host placeholders from its own env. (Fully
  replacing config-server with direct ConfigMaps is a ratified post-MVP change.)

---

## 4. Deploy the platform

### 4.1 Dependencies + services

```sh
# 4.1.1 Dependency stack (creates the `telco` namespace + all stateful backends).
# Give it a long timeout: images are large on first pull and it has a MinIO
# bucket-create post-install hook.
kubectl create namespace telco
helm install telco-deps deploy/helm/dependencies -n telco --set createNamespace=false --timeout 20m

# Wait for the core deps (schema-registry is a known follow-up - see Section 9).
for d in postgres redis mongo kafka keycloak minio; do
  kubectl -n telco rollout status "statefulset/$d" 2>/dev/null || \
  kubectl -n telco wait --for=condition=ready pod -l "app.kubernetes.io/name=$d" --timeout=300s
done

# 4.1.2 Services - install order and the loop are in deploy/helm/README.md.
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

### 4.2 Local Kind: use locally-built images

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

## 5. Access the platform

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

## 6. Scaling (HPA)

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

## 7. Rollback

Full procedure and live evidence: [`deploy/ROLLBACK.md`](ROLLBACK.md). Quick form:

```sh
helm -n telco history <service>        # find the last good REVISION
helm -n telco rollback <service>       # to the previous revision (or `... <REV>`)
kubectl -n telco rollout status deploy/<service> --timeout=180s
```

A bad deploy is detected by failing readiness (a broken image leaves the new pod
NotReady while `maxUnavailable: 0` keeps the old pods serving) and by the smoke
test (Section 8). In CI, smoke failure triggers rollback automatically.

---

## 8. Post-deploy smoke test

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

## 9. Observability

The `dependencies` chart deploys the full stack (ADR-012). All are ClusterIP;
port-forward to reach a UI:

```sh
kubectl -n telco port-forward svc/grafana    3000:3000   # dashboards (platform-overview, kafka-billing, circuit-breakers)
kubectl -n telco port-forward svc/prometheus 9090:9090   # metrics + alerts
# Traces (Tempo) and logs (Loki) are wired as Grafana datasources; services push
# OTLP to otel-collector:4318 and logs to loki:3100 (the `docker` profile addresses).
```

---

## 10. Teardown

```sh
helm -n telco list -q | xargs -r -n1 helm -n telco uninstall   # remove all releases
helm -n telco uninstall telco-deps                             # remove dependencies
kind delete cluster --name telco                               # destroy the cluster
```

---

## 11. Known follow-ups (tracked, non-blocking for the pipeline)

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
