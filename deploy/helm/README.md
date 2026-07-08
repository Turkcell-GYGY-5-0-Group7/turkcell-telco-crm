# Telco CRM - Kubernetes / Helm deployment

This directory holds the Helm charts that deploy the Telco CRM platform to a
Kubernetes cluster (developed and statically validated against a Kind cluster).

```
deploy/helm/
  dependencies/          # in-cluster stateful deps (15.2.3): postgres, redis,
                         # mongo, kafka, schema-registry, kafka-connect, minio,
                         # keycloak, otel-collector, tempo, loki, prometheus, grafana
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
# 1. Dependencies (creates the `telco` namespace + all stateful backends).
helm install telco-deps deploy/helm/dependencies --namespace telco --create-namespace

# 2. Infra services first (config-server before the rest so they can bootstrap).
helm install config-server    deploy/helm/telco-service -n telco -f deploy/helm/values/config-server.yaml    --set image.owner=<OWNER>
helm install discovery-server deploy/helm/telco-service -n telco -f deploy/helm/values/discovery-server.yaml --set image.owner=<OWNER>
helm install api-gateway      deploy/helm/telco-service -n telco -f deploy/helm/values/api-gateway.yaml      --set image.owner=<OWNER>

# 3. Domain services.
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

## Config / Secret model (15.2.2, ADR-010, NFR-06)

Each service's environment (from its `infra/docker/compose.yml` block) is split:

* **Non-secret** env (`config:` map -> per-service **ConfigMap**
  `<service>-config`): config-server URI, profile, usernames, hosts, ports,
  URIs, feature flags. Projected via `envFrom.configMapRef`.
* **Secret** env (`secrets:` map -> per-service **Secret** `<service>-secret`):
  anything ending in `PASSWORD`/`KEY`/`SECRET`/`TOKEN` or otherwise sensitive.
  Projected via `envFrom.secretRef`.

Secret keys (consistent across services and with the `dependencies` chart's
`REDIS_PASSWORD`):

| Secret key             | Used by | Purpose |
| ---------------------- | ------- | ------- |
| `CONFIG_SERVER_PASSWORD` | all except discovery-server | config-server basic-auth |
| `EUREKA_PASSWORD`        | all | Eureka basic-auth |
| `REDIS_PASSWORD`         | api-gateway + 10 domain | Redis auth (matches redis-credentials) |
| `ENCRYPT_KEY`            | config-server | Spring Cloud Config symmetric encrypt key |
| `CUSTOMER_AES_KEY`       | customer-service | AES-256 PII-at-rest key (NFR-06) |

The values in `values/<service>.yaml` under `secrets:` are **DEV-ONLY** defaults
that mirror the compose `${VAR:-default}` fallbacks (same posture as the
`dependencies` chart). **No real/production secret is committed.** For a non-dev
cluster, override at install time:

```sh
helm upgrade config-server deploy/helm/telco-service -n telco \
  -f deploy/helm/values/config-server.yaml \
  --set-file secrets.ENCRYPT_KEY=./encrypt.key \
  --set secrets.CONFIG_SERVER_PASSWORD=$CONFIG_PW
```

or supply a pre-created / external Secret (e.g. Vault, External Secrets
Operator) whose keys match the names above.

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

## Validate (no cluster needed)

```sh
for s in $(ls deploy/helm/values | sed 's/.yaml//'); do
  helm lint     deploy/helm/telco-service -f "deploy/helm/values/$s.yaml"
  helm template "$s" deploy/helm/telco-service -f "deploy/helm/values/$s.yaml" -n telco
done
```
