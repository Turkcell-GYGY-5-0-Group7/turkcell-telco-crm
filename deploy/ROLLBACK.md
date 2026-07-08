# Rollback runbook (Sprint 15.4.2)

> Fast, reversible recovery from a bad deploy (NFR-04, ADR-014).
> Golden rule: every deploy is reproducible and reversible.

Each of the 13 services is its own Helm **release** on the reusable
`deploy/helm/telco-service` chart (one values file per service under
`deploy/helm/values/`). Separate releases mean each service is upgraded and
**rolled back independently** - a bad ticket-service does not force a
subscription-service rollback.

## How a bad deploy is detected

The pipeline (`.github/workflows/deploy.yml`) catches a bad deploy two ways,
both of which drop into the automated rollback step:

1. **Failing readiness during rollout.** The Deployment uses a
   `RollingUpdate` with `maxUnavailable: 0` / `maxSurge: 1`
   (`deploy/helm/telco-service/templates/deployment.yaml`). A bad image or a
   pod that never passes its readiness probe leaves the **new** pod `NotReady`
   while the **old** pods keep serving; `kubectl rollout status` blocks and then
   fails - no traffic was ever shifted to the broken pod.
2. **Smoke-test failure.** `deploy/smoke/smoke-test.sh` runs after the rollout
   and exits non-zero on any failure: gateway `/actuator/health` not `200 UP`,
   a key Deployment not ready, no Keycloak token, or the authenticated read not
   returning its expected status. A non-zero exit fails the job.

Either failure makes the `Rollback on failure` step (`if: failure()`) run, then
the pipeline is left **red**.

## Automated rollback (in CI)

The deploy job's rollback step iterates every service release and, for any
release that has a **prior revision** (`helm history` length >= 2), runs:

```sh
helm -n telco rollback <release>
```

which reverts that release to its last good revision.

### Ephemeral-cluster caveat

The CI target is a **throwaway Kind cluster created fresh per run**, so every
release is at **revision 1** - there is no previous revision to revert to. On a
bad deploy the practical outcome is: the pipeline goes red, nothing was ever
serving external traffic, and the whole cluster is torn down at job end, so no
broken state ships. The `helm rollback` wiring is still exercised and is
**identical** to what runs against a persistent cluster; it simply reports
"nothing to roll back" for a first-revision release. On a persistent
environment the same step restores each release to its last good revision.

## Manual rollback (persistent cluster)

Inspect history and revert a single release:

```sh
# See every revision, its chart/app version, and status.
helm -n telco history customer-service

# Roll back to the immediately previous revision.
helm -n telco rollback customer-service

# Or roll back to a specific known-good revision number.
helm -n telco rollback customer-service 3

# Confirm the workload converged back to Ready.
kubectl -n telco rollout status deploy/customer-service
```

Roll back everything (rare - prefer per-service):

```sh
for rel in config-server discovery-server api-gateway identity-service \
           customer-service product-catalog-service order-service \
           subscription-service usage-service billing-service payment-service \
           notification-service ticket-service; do
  helm -n telco history "$rel" >/dev/null 2>&1 && helm -n telco rollback "$rel" || true
done
```

Re-run the smoke test to confirm service was restored:

```sh
NAMESPACE=telco GATEWAY_URL=http://localhost:18080 INGRESS_HOST=telco.local \
  deploy/smoke/smoke-test.sh
```

## Verified evidence (live-proven)

The `maxUnavailable: 0` "detect failing readiness -> rollback" flow is verified
live on Kind:

1. A release is upgraded to a **bad image tag**.
2. Because `maxUnavailable: 0`, the old pods keep serving while the new pod
   stays `NotReady` (`ImagePullBackOff` / failing readiness) - **no outage**.
3. `helm -n telco rollback <release>` restores the last good revision and the
   Deployment returns to Ready.

This is exactly the path the pipeline automates: a broken revision never
displaces healthy pods, and recovery is a single `helm rollback`.
