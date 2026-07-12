# Sprint 18 - Secret Management (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| DONE (features); exit-criteria tail tracked | 5/5 | 2026-07-12 |

Legend: DONE / IN PROGRESS / TODO / BLOCKED / DEFERRED. Cross-sprint rollup: [../STATUS.md](../STATUS.md).

> This is a **post-MVP** sprint. The MVP is Sprints 01-15 (backend + Swagger). It is documented now
> and built later (ADR-025). Feature subtask files will be authored when the sprint is scheduled.

## Objective

Stand up **HashiCorp Vault** as the platform's centralized secret store per ADR-025, and migrate the
Sprint 15 Helm deployment off committed dev-default Kubernetes Secrets. Concretely: `ENCRYPT_KEY`
(config-server), `CUSTOMER_AES_KEY` (customer-service PII key, NFR-06), `CONFIG_SERVER_PASSWORD` /
`EUREKA_PASSWORD` / `REDIS_PASSWORD` (all services), and per-service database credentials - the last of
which are not secreted at all today (they are baked in plaintext into the `docker`-profile YAML
config-server serves, per ADR-025's Context section) - all move to Vault, delivered into pods via the
Secrets Store CSI Driver's `secretObjects` sync to native Kubernetes Secrets, so every service's
existing `envFrom.secretRef` consumption is unchanged. config-server's role serving bulk, non-secret
configuration is unchanged (ADR-010); this sprint touches only the secret half of the Sprint 15 config/
secret split.

Envelope encryption / key-id rotation for the AES-GCM PII converter and `starter-crypto` extraction are
explicitly **out of scope** for this sprint (ADR-025 Section 3) - a future ADR and sprint.

## Included Epics

- Epic 18: Secret Management (Vault, Kubernetes auth method, CSI-synced secrets)

## Features (one file per top-level task)

| ID | Feature | Status | File |
| --- | --- | --- | --- |
| 18.1 | Vault Helm release: standalone server, Raft storage, unseal procedure | DONE | [18.1-vault-helm-release-standalone-raft-storage-unseal-procedure.md](18.1-vault-helm-release-standalone-raft-storage-unseal-procedure.md) |
| 18.2 | Kubernetes auth method + per-service policies bound to existing `ServiceAccount`s | DONE | [18.2-kubernetes-auth-method-and-per-service-policies.md](18.2-kubernetes-auth-method-and-per-service-policies.md) |
| 18.3 | Secrets Store CSI Driver + Vault CSI provider, `SecretProviderClass` per service, `secretObjects` sync to `<service>-secret` | DONE | [18.3-secrets-store-csi-driver-and-secretproviderclass-sync.md](18.3-secrets-store-csi-driver-and-secretproviderclass-sync.md) |
| 18.4 | Migrate `ENCRYPT_KEY`, `CUSTOMER_AES_KEY`, `CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD` from committed Helm defaults into Vault KV v2 | DONE | [18.4-migrate-core-secrets-to-vault-kv-v2.md](18.4-migrate-core-secrets-to-vault-kv-v2.md) |
| 18.5 | Per-service DB credentials into Vault KV v2; retire the `docker`-profile plaintext DB block for in-cluster deployment in favor of an environment-sourced profile | DONE | [18.5-per-service-db-credentials-to-vault-and-retire-docker-profile-plaintext.md](18.5-per-service-db-credentials-to-vault-and-retire-docker-profile-plaintext.md) |

## Sprint Deliverables

- A `deploy/helm/vault/` (or equivalent) Vault release, installed the same way `dependencies` and
  `telco-service` are installed, with a documented unseal procedure.
- Vault Kubernetes auth roles and policies, one per service, each scoped to that service's own KV path
  only - least privilege, mirroring the boundary the per-service `<service>-secret` object already
  gives today.
- The `telco-service` chart's Secret source changed from a static `secrets:` values map to a
  CSI-synced Secret, with **zero change** to any service's `envFrom`, Spring configuration, or
  Dockerfile.
- No committed real secret value anywhere in `deploy/helm/values/*.yaml` after this sprint; the
  DEV-ONLY defaults called out in `deploy/helm/README.md` and `deploy/RUNBOOK.md` Section 4
  ("Configuration and secrets") are retired in favor of Vault-sourced values for any non-local
  environment.
- Per-service database credentials sourced from Vault instead of the plaintext `docker`-profile block,
  closing the gap identified in ADR-025's Context section.

## Exit Criteria

- MET - ADR-025 is ratified (Accepted) by tech-lead before any code in this sprint ships (the ADR is
  Proposed as of this drafting).
- NOT MET, tracked - a pod for every one of the 13 services starts successfully with its secret
  environment populated from a Vault-sourced, CSI-synced Kubernetes Secret - verified live on the
  Sprint 15 Kind cluster (`deploy/RUNBOOK.md` Section 2), the same verification standard Sprint 15
  itself used. Each of the 5 features individually met its own subtask-level acceptance criteria
  (config-server reached full `Ready`; DB-credential delivery was proven directly at the network/psql
  level for 2 services since app-level `Ready` is currently blocked platform-wide), but the sprint-wide
  "every one of the 13 services" framing is not literally true today. The blocker is a pre-existing,
  Sprint-18-unrelated config-server bug (`FailedToConstructEnvironmentException: found duplicate key
  spring`, merging the shared `application-dev.yml` with a service's own) that reproduces for every
  service regardless of which Vault/CSI change this sprint made - flagged for domain-engineer/
  event-integration as a follow-up, mirroring how Sprint 15 left its own "full 13-service boot" item
  open as a tracked tail rather than blocking that sprint's feature-level DONE status. See the Feature
  18.4/18.5 live-verification records below for the exact per-service evidence.
- MET - deleting a service's Vault policy (simulating misconfiguration/least-privilege enforcement)
  prevents that service's `SecretProviderClass` from syncing, proving the per-service scoping is real
  and not merely documented (live-verified in Feature 18.4).
- MET - `deploy/helm/README.md` and `deploy/RUNBOOK.md` are updated to describe the Vault-backed secret
  flow, replacing the "DEV-ONLY defaults... override at install time" guidance with the Vault procedure.
- MET - no application code, Dockerfile, or `envFrom` reference changes in any of the 13 services.

## Feature 18.5.2 audit: `application-prod.yml` vs `application-docker.yml` per PostgreSQL-backed service

Per ADR-025 Section 4's deferred audit. For every one of the 10 PostgreSQL-backed services
(`docs/architecture/service-catalog.md` Section 5), `application-prod.yml` was compared against
`application-docker.yml` (both under `microservices/configs/<service>/`). Finding, identical in shape
across all 10 services:

| Service | DB creds externalized in `prod`? | Other divergences `prod` drops vs `docker` | Safe to activate `prod` in-cluster as-is? | Decision |
| --- | --- | --- | --- | --- |
| identity-service | Yes (`IDENTITY_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers` (falls back to `localhost:9092`), Keycloak JWKS URI (falls back to `localhost:8085`), `keycloak.admin.server-url` | No | `application-k8s.yml` |
| customer-service | Yes (`CUSTOMER_DB_URL/USER/PASSWORD`) | Keycloak JWKS URI; MinIO endpoint/access-key/secret-key (`prod` requires `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` env vars not set in this cluster - out of this feature's DB-credential scope) | No | `application-k8s.yml` |
| product-catalog-service | Yes (`CATALOG_DB_URL/USER/PASSWORD`) | Redis `host` (`prod` requires `REDIS_HOST` not set; `docker` hardcodes `redis`), Keycloak JWKS URI | No | `application-k8s.yml` |
| order-service | Yes (`ORDER_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Keycloak JWKS URI, `telco.clients.customer-service`/`product-catalog-service` URLs | No | `application-k8s.yml` |
| subscription-service | Yes (`SUBSCRIPTION_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Keycloak JWKS URI, `telco.clients.order` URL | No | `application-k8s.yml` |
| usage-service | Yes (`USAGE_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Redis `host`, Keycloak JWKS URI, `telco.clients.product-catalog-service` URL | No | `application-k8s.yml` |
| billing-service | Yes (`BILLING_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Keycloak JWKS URI, `telco.clients.product-catalog-service` URL, MinIO endpoint/access-key/secret-key | No | `application-k8s.yml` |
| payment-service | Yes (`PAYMENT_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Redis `host`, Keycloak JWKS URI | No | `application-k8s.yml` |
| notification-service | Yes (`NOTIFICATION_DB_URL/USER/PASSWORD`, `NOTIFICATION_MONGO_URI`) | Kafka `bootstrap-servers`, Keycloak JWKS URI | No | `application-k8s.yml` |
| ticket-service | Yes (`TICKET_DB_URL/USER/PASSWORD`) | Kafka `bootstrap-servers`, Keycloak JWKS URI | No | `application-k8s.yml` |

Conclusion: `prod` is **not** safe to activate in-cluster as-is for any of the 10 services - every one
of them would lose Kafka connectivity and/or JWT validation and/or inter-service client URLs if `prod`
were used bare, reintroducing exactly the "docker-profile override missing" bug class already found and
fixed twice in Sprint 15/17 (`docs/tasks/STATUS.md`, order-service and usage-service entries). All 10
services get a new `microservices/configs/<service>/application-k8s.yml`: functionally
`application-docker.yml` with only the DB username/password fields replaced by
`${<SERVICE>_DB_USER}`/`${<SERVICE>_DB_PASSWORD}`-style placeholders (matching `application-prod.yml`'s
own naming convention). The jdbc URL's host/port/database-name are not secret and stay hardcoded,
unchanged from `application-docker.yml`, rather than moving to Vault or a new ConfigMap entry (ADR-025
Section 2's secret-vs-non-secret distinction). `application-prod.yml` itself is untouched and remains
available for a future environment with real external MinIO/SMTP/PSP/JWKS endpoints.
`deploy/helm/values/<service>.yaml`'s `SPRING_PROFILES_ACTIVE` moved from `dev,docker` to `dev,k8s` for
all 10 services. `api-gateway`, `discovery-server`, `config-server` are not PostgreSQL-backed and are
untouched by this feature.

## Feature 18.5 live-verification record (2026-07-12)

Full detail: `docs/tasks/STATUS.md` dated entry (2026-07-12, Feature 18.5). Summary, stated honestly:

- **Met**: extended `deploy/helm/vault/seed-secrets.sh` (18.5.1) ran successfully for all 10
  PostgreSQL-backed services, writing a real, non-`<service>`/`<service>` password to
  `secret/<service>/db-credentials` for each AND rotating the matching live Postgres role's password
  (`ALTER USER`) so Vault and Postgres stay in sync. Spot-checked `order-service` and `billing-service`:
  Vault returned password values distinct from the committed `order`/`order` and `billing`/`billing`
  defaults. Confirmed each service's existing 18.2.2 policy already covers `secret/data/<service>/*`
  (the `db-credentials` path) - no new policy was written.
- **Met**: `microservices/configs/<service>/application-k8s.yml` created for all 10 services;
  `deploy/helm/values/<service>.yaml`'s `SPRING_PROFILES_ACTIVE` changed from `dev,docker` to `dev,k8s`
  for all 10, plus matching `vault.secretKeys` entries (`<SERVICE>_DB_USER`/`<SERVICE>_DB_PASSWORD`) added
  to sync `secret/<service>/db-credentials` via the existing, unmodified `SecretProviderClass` template.
- **Met, with an important scope-widening finding**: deployed `billing-service` (newly built/`kind
  load`ed image) and re-deployed `customer-service` with `vault.enabled=true` and the new `dev,k8s`
  profile. Neither reaches full pod `Ready` - **not because of anything this feature introduced**, but
  because the config-server native-repository bug 18.4 flagged (`FailedToConstructEnvironmentException:
  ... found duplicate key spring`) is confirmed live to be platform-wide, not customer-service- or
  docker-profile-specific: `config-server`'s `/{service}/{profile}` endpoint 500s for every service with
  its own `application-dev.yml` regardless of the second profile requested (`dev`, `dev,docker`, and
  `dev,k8s` all reproduce it identically for `billing-service`, `customer-service`, `identity-service`,
  `ticket-service`, `order-service`, and even `api-gateway` on its original `dev,docker` profile,
  confirming this predates and is unrelated to 18.5's changes). Switching the second profile name does
  **not** work around it, since the merge conflict is between the root `application-dev.yml` and each
  service's own `application-dev.yml`. **Not fixed here** (still domain-engineer/event-integration
  territory, not `deploy/`), but the finding is now more precisely scoped than 18.4 left it.
- **Met via a direct, app-independent proof instead**: because config fetch failing means the app never
  reaches `DataSource` creation, "full `Ready` implies DB connectivity" could not be used. Instead: (1)
  `kubectl exec ... -- env` on both `billing-service` and `customer-service` confirmed
  `BILLING_DB_USER`/`BILLING_DB_PASSWORD` and `CUSTOMER_DB_USER`/`CUSTOMER_DB_PASSWORD` exactly match
  Vault's `secret/<service>/db-credentials` (CSI sync proven correct); (2) from `postgres-0`, a direct
  `psql` connection to Postgres's **Service IP** (not `localhost`, which hits a `trust`-auth rule and
  proves nothing) using each service's injected credential succeeded, and the **same connection with the
  old `billing`/`billing` or `customer`/`customer` default failed with `password authentication failed`**
  - proving the rotated credential is real and required, end to end, independent of the blocked app-boot
  path.
- **Not attempted**: the direct psql-level proof above was only done for 2 of the 10 services
  (`billing-service`, `customer-service`) - the other 8 have real Vault-held, Postgres-rotated
  credentials (confirmed present in Vault) but were not individually proven to authenticate over the
  network in this session. Do not read this as "all 10 services proven DB-connectivity-live."
- **Met**: confirmed no touched service's `SPRING_PROFILES_ACTIVE` retains `docker` - live for
  `billing-service`/`customer-service` (`dev,k8s`), and by inspection of the committed
  `deploy/helm/values/*.yaml` for the remaining 8.
- Kind cluster torn down after this session (last Sprint 18 feature; no further feature needs it).

## Feature 18.4 live-verification record (2026-07-12)

Full detail: `docs/tasks/STATUS.md` dated entry (2026-07-12, Feature 18.4). Summary, stated honestly
against this sprint's Exit Criteria above:

- **Met**: `deploy/helm/vault/seed-secrets.sh` (18.4.1) generated and wrote real values for all five
  credentials at the exact Vault KV v2 paths Feature 18.3's `vault.secretKeys` expect;
  `secret/config-server/encrypt-key` verified as a 64-hex-char value distinct from the retired dev
  default, `secret/customer-service/aes-key` verified to decode to exactly 32 bytes.
- **Met (minimum bar)**: `config-server` reached full `1/1 Ready` with `vault.enabled=true`, and
  `kubectl exec ... -- env` confirmed real Vault-generated values, not dev defaults.
  `customer-service` reached `Running` (not full `Ready`) with the same real-value confirmation, and its
  Basic-Auth to config-server was proven to succeed with the real Vault-sourced
  `CONFIG_SERVER_PASSWORD` (previously 401, now authenticated) - the secret-delivery chain this feature
  owns is proven end-to-end for both named services.
- **Met**: the policy-deletion negative test (`vault policy delete customer-service` -> pod recreate)
  produced a live `FailedMount` / `403 permission denied` event, then succeeded again once the policy
  was restored.
- **Not attempted / not met**: the sprint README's broader "all 13 services boot" framing. Only
  config-server and customer-service were installed against the live Vault-backed chart in this session
  (consistent with Sprint 15's own unresolved tail: only 4 of 13 services were ever gotten to a full
  live boot in any prior session). This is out of Feature 18.4.3's stated scope per its own task spec
  and is not required for 18.4 to be marked DONE.
- **New finding, not fixed here (out of `deploy/` scope)**: `customer-service`'s pod does not reach full
  `Ready` because of a newly discovered, pre-existing, Vault-unrelated bug - config-server's native
  repository fails to merge `microservices/configs/application-dev.yml` with
  `microservices/configs/<service>/application-dev.yml` (both declare a top-level `spring:` key) for
  11 of the 13 services when a multi-profile request (`dev,docker`) is made
  (`FailedToConstructEnvironmentException: ... found duplicate key spring`). Flagged for
  domain-engineer/event-integration as a follow-up; not a Sprint 18 or `deploy/` concern.
- Two bugs found and fixed as part of this feature (both minimal, both documented at the point of fix):
  a dev-only hardcoded Basic-Auth probe header in `deploy/helm/values/config-server.yaml` that broke
  probes once `CONFIG_SERVER_PASSWORD` became a real value, and a Git-Bash-on-Windows `\r`
  (CRLF)-stripping bug in `seed-secrets.sh` itself that silently corrupted every generated password.

## References

- [ADR-025 Secrets and Key Management](../../../architecture/adr/ADR-025-secrets-and-key-management.md)
- [docs/architecture/security-posture.md](../../architecture/security-posture.md) Section 5 (Key
  management and rotation) and Section 10 (hardening checklist) - the current-state record this
  sprint's ADR supersedes in part.
- [docs/product/TELCO-CRM-ADVANCED.md](../../product/TELCO-CRM-ADVANCED.md) Section 4.3 (Cryptography
  and Secrets) - the forward-looking design this sprint delivers the Vault half of.
- [deploy/helm/README.md](../../../deploy/helm/README.md) ("Config / Secret model") - the Sprint 15
  split this sprint evolves.
- [deploy/RUNBOOK.md](../../../deploy/RUNBOOK.md) Section 4 (Configuration and secrets) and Section 3
  (Vault initialization and unseal, added by 18.1) - the operational doc this sprint updates.
- [Sprint 15 - Deployment](../sprint-15-deployment/README.md) - the Helm chart and K8s Secret model
  this sprint builds on.
- `microservices/customer-service/src/main/java/com/telco/customer/infrastructure/crypto/AesKeyProvider.java` -
  the key consumer whose Javadoc already names Vault as the staging/prod source.
- `microservices/configs/customer-service/application-docker.yml` and `application-prod.yml` - the
  plaintext-vs-externalized DB credential gap Feature 18.5 closes.
