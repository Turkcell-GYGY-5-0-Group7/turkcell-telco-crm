# Sprint 18 - Secret Management (post-MVP)

| Status | Progress | Last updated |
| --- | --- | --- |
| TODO | 0/5 | 2026-07-11 |

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
| 18.1 | Vault Helm release: standalone server, Raft storage, unseal procedure | TODO | [18.1-vault-helm-release-standalone-raft-storage-unseal-procedure.md](18.1-vault-helm-release-standalone-raft-storage-unseal-procedure.md) |
| 18.2 | Kubernetes auth method + per-service policies bound to existing `ServiceAccount`s | TODO | [18.2-kubernetes-auth-method-and-per-service-policies.md](18.2-kubernetes-auth-method-and-per-service-policies.md) |
| 18.3 | Secrets Store CSI Driver + Vault CSI provider, `SecretProviderClass` per service, `secretObjects` sync to `<service>-secret` | TODO | [18.3-secrets-store-csi-driver-and-secretproviderclass-sync.md](18.3-secrets-store-csi-driver-and-secretproviderclass-sync.md) |
| 18.4 | Migrate `ENCRYPT_KEY`, `CUSTOMER_AES_KEY`, `CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD` from committed Helm defaults into Vault KV v2 | TODO | [18.4-migrate-core-secrets-to-vault-kv-v2.md](18.4-migrate-core-secrets-to-vault-kv-v2.md) |
| 18.5 | Per-service DB credentials into Vault KV v2; retire the `docker`-profile plaintext DB block for in-cluster deployment in favor of an environment-sourced profile | TODO | [18.5-per-service-db-credentials-to-vault-and-retire-docker-profile-plaintext.md](18.5-per-service-db-credentials-to-vault-and-retire-docker-profile-plaintext.md) |

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
  DEV-ONLY defaults called out in `deploy/helm/README.md` and `deploy/RUNBOOK.md` Section 3 are
  retired in favor of Vault-sourced values for any non-local environment.
- Per-service database credentials sourced from Vault instead of the plaintext `docker`-profile block,
  closing the gap identified in ADR-025's Context section.

## Exit Criteria

- ADR-025 is ratified (Accepted) by tech-lead before any code in this sprint ships (the ADR is
  Proposed as of this drafting).
- A pod for every one of the 13 services starts successfully with its secret environment populated
  from a Vault-sourced, CSI-synced Kubernetes Secret - verified live on the Sprint 15 Kind cluster
  (`deploy/RUNBOOK.md` Section 2), the same verification standard Sprint 15 itself used.
- Deleting a service's Vault policy (simulating misconfiguration/least-privilege enforcement) prevents
  that service's `SecretProviderClass` from syncing, proving the per-service scoping is real and not
  merely documented.
- `deploy/helm/README.md` and `deploy/RUNBOOK.md` are updated to describe the Vault-backed secret flow,
  replacing the "DEV-ONLY defaults... override at install time" guidance with the Vault procedure.
- No application code, Dockerfile, or `envFrom` reference changes in any of the 13 services.

## References

- [ADR-025 Secrets and Key Management](../../../architecture/adr/ADR-025-secrets-and-key-management.md)
- [docs/architecture/security-posture.md](../../architecture/security-posture.md) Section 5 (Key
  management and rotation) and Section 10 (hardening checklist) - the current-state record this
  sprint's ADR supersedes in part.
- [docs/product/TELCO-CRM-ADVANCED.md](../../product/TELCO-CRM-ADVANCED.md) Section 4.3 (Cryptography
  and Secrets) - the forward-looking design this sprint delivers the Vault half of.
- [deploy/helm/README.md](../../../deploy/helm/README.md) ("Config / Secret model") - the Sprint 15
  split this sprint evolves.
- [deploy/RUNBOOK.md](../../../deploy/RUNBOOK.md) Section 3 (Configuration and secrets) - the
  operational doc this sprint updates.
- [Sprint 15 - Deployment](../sprint-15-deployment/README.md) - the Helm chart and K8s Secret model
  this sprint builds on.
- `microservices/customer-service/src/main/java/com/telco/customer/infrastructure/crypto/AesKeyProvider.java` -
  the key consumer whose Javadoc already names Vault as the staging/prod source.
- `microservices/configs/customer-service/application-docker.yml` and `application-prod.yml` - the
  plaintext-vs-externalized DB credential gap Feature 18.5 closes.
