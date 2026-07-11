# ADR-025 Secrets and Key Management (HashiCorp Vault)

Status: Proposed
Date: 2026-07-11

---

## Context

Sprint 15 (`docs/tasks/sprint-15-deployment/`) wired a Kubernetes Secret/ConfigMap split for the
13-service Helm deployment (`deploy/helm/README.md` "Config / Secret model", ADR-010): each service's
compose environment was partitioned into a non-secret `ConfigMap` and a secret `Secret`, both
projected via `envFrom`. That split works, but three things about it are explicitly flagged as
interim in the artifacts that created it:

* `deploy/helm/README.md` and `deploy/RUNBOOK.md` Section 3 both state the `secrets:` values in
  `deploy/helm/values/*.yaml` are **DEV-ONLY defaults** ("no real/production secret is committed")
  and point at "Vault, External Secrets Operator" as the eventual source.
* `docs/tasks/STATUS.md` (Sprint 15, Feature 15.2) records a reconciliation flagged for
  code-review/tech-lead at sprint close: "config-server stays deployed serving the bulk (baked)
  config while secrets come from K8s Secrets - full config-server removal (pure ConfigMap-per-service)
  is post-MVP." This ADR is the tech-lead-track answer to that reconciliation for the *secrets* half
  of it (config-server's role in serving bulk, non-secret config is unchanged and out of scope here).
* `microservices/customer-service/.../AesKeyProvider.java` already documents the target state in its
  Javadoc: "in staging/prod it is injected from a Kubernetes Secret / Vault."

`docs/architecture/security-posture.md` Section 5 (Key management and rotation) is the authoritative
current record for the AES-256 PII-at-rest key. It documents today's single-active-key model and a
"Production recommendation" of envelope encryption, a key-id/multi-key provider, extraction into a
platform `starter-crypto`, and a KMS/HSM-backed DEK. Section 10's hardening checklist carries two open
items this ADR must dispose of:

* `[ ] starter-crypto extraction + key-id/envelope encryption for zero-downtime key rotation, DEK
  backed by KMS/HSM.`
* `[ ] Real secrets from Vault/K8s Secret; HTTPS everywhere; sslRequired enforced; per-env realms.`

`docs/product/TELCO-CRM-ADVANCED.md` Section 4.3 (Cryptography and Secrets) already names the target
capability ("Centralized secrets (Vault) with dynamic, short-lived credentials; no static DB
passwords") and lists "Secrets, tokenization, and key management (Vault + HSM/KMS)" as a proposed ADR
in Section 9. This ADR is that ADR, scoped to what is implementable at this platform's current size
(13 services, one Kind-based cluster, Helm-charted, per ADR-017/service-catalog) rather than the full
enterprise-scale design.

### An additional finding that sharpens the scope

Grepping the actual config tree surfaces a gap the "Config / Secret model" summary does not mention:
per-service database credentials are **not** in a Kubernetes Secret at all today. They are baked in
plaintext into the profile-scoped YAML that config-server serves, for example
`microservices/configs/customer-service/application-docker.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/customer_db
    username: customer
    password: customer
```

This is fine for local `docker`/`dev` profiles (dev-only, matches the compose posture) but the same
`docker` profile is what Sprint 15's Helm chart runs in-cluster (`deploy/helm/README.md`: "Services run
with `SPRING_PROFILES_ACTIVE=dev,docker` ... there is no `k8s` profile"). The `application-prod.yml`
files (e.g. customer-service's, using `${CUSTOMER_DB_URL}` / `${CUSTOMER_DB_USER}` /
`${CUSTOMER_DB_PASSWORD}`) already anticipate externalized DB credentials, but nothing today populates
those placeholders from a Secret - the `prod` profile is not yet exercised by the Helm chart. DB
credentials are therefore the least-protected secret class in the platform today, not an
already-secreted one, and this ADR treats closing that gap as in scope alongside the two checklist
items above.

---

## Decision

We adopt **HashiCorp Vault** as the platform's centralized secret store, and the **Secrets Store CSI
Driver with the Vault CSI provider, syncing to native Kubernetes Secrets**, as the delivery mechanism
into the existing Helm chart. We explicitly do **not** adopt the Vault Agent sidecar injector, and we
explicitly scope AES-GCM envelope encryption / key-id rotation **out** of this ADR.

### 1. Vault deployment model

* Vault runs **in-cluster**, installed via the official `hashicorp/vault` Helm chart as a fourth
  chart alongside `dependencies`, `telco-service`, and (per ADR-022) the frontend/BFF chart -
  consistent with the "one chart per concern, one release per moving part" pattern already
  established in `deploy/helm/`.
* **Server mode: standalone, Integrated Storage (Raft), single node** for this platform's current
  scale (13 services, one Kind-based cluster, Sprint 15's deployment target). A 3-5 node Raft cluster
  with auto-unseal (cloud KMS or an upstream Vault's Transit auto-unseal) is the documented production
  upgrade path but is not required to deliver this ADR's scope; a single-node Vault with Shamir-sealed
  manual unseal is an accepted MVP posture, mirroring the same "dev-grade in-cluster dependency,
  hardened later" posture the `dependencies` chart already takes for Postgres/Redis/Kafka. This keeps
  operational surface proportionate: introducing a full HA Raft cluster and auto-unseal integration
  for a single-cluster, 13-service platform would be over-engineering relative to the current blast
  radius, and the Raft storage backend keeps the upgrade path to HA a scaling exercise, not a
  migration.
* **Authentication: the Kubernetes auth method.** Each Helm release already provisions its own
  `ServiceAccount` (`deploy/helm/telco-service/templates/serviceaccount.yaml`); Vault is configured
  with one auth role per service, bound to that service's `ServiceAccount` name and the `telco`
  namespace, mapped to a Vault policy scoped to exactly that service's KV path
  (`secret/data/<service>/*`, read-only). This mirrors the least-privilege boundary the per-service
  `<service>-secret` object already gives today - no service can read another service's secrets,
  now enforced by Vault policy instead of by convention.
* **Delivery: Secrets Store CSI Driver + Vault CSI provider, with `secretObjects` sync to native K8s
  Secrets** - not the Vault Agent sidecar injector. Reasoning: the injector delivers secrets as files
  into a shared volume and requires either an app-level template/env-file consumption pattern (extra
  init/entrypoint work across all 13 Dockerfiles) or a second sidecar process per pod; Spring Boot's
  config model in this platform is env-var driven (`@Value`, `${VAR}` placeholders in
  `microservices/configs/`), not file driven. The CSI driver's `secretObjects` feature mounts the
  Vault secret via a `SecretProviderClass` **and** materializes it as an ordinary Kubernetes `Secret`
  with the same key names used today (`ENCRYPT_KEY`, `CUSTOMER_AES_KEY`, `CONFIG_SERVER_PASSWORD`,
  ...), which the chart already consumes via `envFrom.secretRef`. This is the delivery mode that
  requires **zero change** to `deployment.yaml`'s `envFrom`, zero change to any service's Spring
  config, and zero change to any Dockerfile - only the `telco-service` chart's Secret *source*
  changes (a `SecretProviderClass` + a synced Secret, replacing a static `secrets:` values map), which
  is a chart-only concern for a later devops-owned sprint (Sprint 18, see below). This is a deliberate
  pick, not a hedge: for this platform's Spring-Boot/env-var config model the CSI-sync path has
  materially lower blast radius than the sidecar injector for equivalent security outcome.

### 2. What moves to Vault vs what stays a Kubernetes Secret vs what stays a ConfigMap

| Item | Today | Decision |
| --- | --- | --- |
| `ENCRYPT_KEY` (config-server symmetric encrypt key) | Static K8s Secret, dev-default committed | **Move to Vault** KV v2 (`secret/config-server/encrypt-key`), CSI-synced to the existing `config-server-secret` object |
| `CUSTOMER_AES_KEY` (PII-at-rest key, NFR-06) | Static K8s Secret, dev-default committed | **Move to Vault** KV v2 (`secret/customer-service/aes-key`); see Section 3 for rotation scope |
| `CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD` | Static K8s Secret, one shared dev-default value reused across all services | **Move to Vault** KV v2, one value per credential (still shared where the backing system requires a single credential, e.g. one Redis password), generated per environment instead of committed |
| Per-service DB username/password (`customer`/`customer`, etc.) | **Not secreted at all** - plaintext in the `docker`-profile YAML config-server bakes and serves (see Context) | **Move to Vault** KV v2 (`secret/<service>/db-credentials`), one policy-scoped path per service; this closes the gap identified above. Populated via the `prod` profile's `${CUSTOMER_DB_URL}`/`${CUSTOMER_DB_USER}`/`${CUSTOMER_DB_PASSWORD}`-style placeholders (already present, unused), not the `docker` profile's plaintext block, once Sprint 18 retires the in-cluster use of the `docker` profile in favor of a profile that reads these placeholders |
| PSP credentials (payment-service) | **Do not exist as a secret today** - `payment-service` talks to `MockPspAdapter` (`infrastructure/psp/MockPspAdapter.java`); the only related config is `PSP_MOCK_FORCE_OUTCOME`, a non-secret behavior flag already in the `config:` ConfigMap | No migration needed now (there is nothing to migrate). **Decision for the future real-PSP integration** (TELCO-CRM-ADVANCED Section 2, out of this ADR's delivery scope): any real PSP API key / webhook signing secret MUST be sourced from Vault from the day it is introduced, never committed to a values file or ConfigMap. Recorded here so the constraint exists before the integration is built |
| Non-secret config (`CONFIG_SERVER_URI`, profile, hostnames, `PSP_MOCK_FORCE_OUTCOME`, feature flags) | K8s ConfigMap, served in bulk by config-server | **Unchanged.** Stays a ConfigMap; Vault is a secret store, not a config store, and nothing here is a credential |

### 3. Envelope encryption / key-id rotation for the AES-GCM PII converter: explicitly out of scope

`security-posture.md` Section 5's production recommendation bundles two different concerns:
(a) *where the key material is sourced and how operators rotate it operationally*, and (b) *how the
application-level crypto works* (envelope encryption, a `key_id` column, a multi-key provider,
dual-read/single-write decrypt, extraction into a platform `starter-crypto`). This ADR resolves only
(a). (b) requires a schema migration (`identity_number_enc` gains a key-id column), a converter
rewrite, and a new platform starter - domain-engineer- and platform-engineer-owned work with a
different blast radius and review path than standing up Vault. Bundling it here would make this ADR
both a secrets-infrastructure decision and a data-model decision, which this platform's ADR
granularity (one ADR, one decision) argues against.

Concretely:

* **In scope (this ADR):** `CUSTOMER_AES_KEY`'s raw key material moves from a committed Helm default
  to a Vault KV v2 secret. Vault KV v2's built-in version history gives the rotation runbook in
  `security-posture.md` Section 5 ("Rotation procedure (single active key, MVP)") an audited record of
  every key version even though the *converter* still only supports one active key - a strict
  improvement over today with no code change required.
  Vault Kubernetes auth also gives per-service RBAC over who can even read the key (only
  customer-service's `ServiceAccount`), which the current committed-Helm-default model has no
  equivalent of. Rotation still requires the same offline re-encrypt-and-swap procedure documented in
  `security-posture.md` Section 5 - that procedure is unchanged by this ADR.
* **Out of scope (future ADR):** envelope encryption, key-id/multi-key `starter-crypto`, and
  KMS/HSM-backed DEKs remain the open checklist item in `security-posture.md` Section 10. When that
  future ADR is written, it should target **Vault's Transit secrets engine** as the envelope-encryption
  mechanism (encrypt-as-a-service, native key versioning, native rotation) rather than a hand-rolled
  KMS/HSM integration, since Vault will already be the platform's trust root for secrets by then. This
  is a recommendation for the future ADR's author, not a decision this ADR makes.

### 4. Interaction with config-server

The split recorded in `docs/tasks/STATUS.md` (Sprint 15) - "config-server stays deployed serving the
bulk (baked) config while secrets come from K8s Secrets" - **continues, with Vault replacing the
*source* of the Secret half, not the split itself.** Concretely:

* config-server keeps serving everything currently in `microservices/configs/` (URLs, profiles,
  feature flags, timeouts, non-secret per-env overrides) exactly as ADR-010's amendment describes. No
  change to config-server's role, its native filesystem backend, or its exemption from
  `spring.config.import`.
* Where a config-server-served YAML file already externalizes a value via a placeholder
  (`${CUSTOMER_AES_KEY}`, `${CUSTOMER_DB_PASSWORD}`, ...), that placeholder continues to be resolved
  from the pod's environment exactly as today - only *how the pod's environment gets populated*
  changes (CSI-synced Vault secret instead of a static Helm value).
* One consequence follows from the Section 2 finding: in-cluster services must move from the `docker`
  profile (which bakes plaintext DB credentials) to a profile that reads DB credentials from the
  environment (the existing `prod` profile already does this, or a new `k8s` profile if `prod` also
  carries other environment-specific assumptions not yet audited). That profile change is
  implementation, not decided further here - it is Sprint 18 scope (see the companion sprint README).
* Vault does **not** become a second config source competing with config-server. It is strictly a
  secret store; config-server remains the single source of non-secret configuration, unchanged.

---

## Consequences

### Positive

* Closes two open items in `security-posture.md` Section 10's hardening checklist (secrets sourcing;
  a documented, if partial, step toward the key-rotation item).
* Closes an undocumented gap this ADR's research surfaced: per-service DB credentials move from
  plaintext-in-baked-config to a Vault-sourced, per-service-scoped secret.
* Zero application code change and zero Dockerfile change: the CSI-sync delivery mode preserves the
  existing `envFrom.secretRef` consumption pattern in every service.
* Least-privilege secret access enforced by Vault policy per service, replacing "any Secret exists,
  any pod in the namespace could theoretically read it via RBAC misconfiguration" with an explicit,
  per-service-bound Vault role.
* Establishes the platform's first real (non-committed) secret material, which is a prerequisite for
  any production environment, not just a hardening nicety.

### Negative

* A new stateful, security-critical component (Vault) to operate, back up, and (eventually) unseal
  under an HA/auto-unseal design - real operational surface added to a platform that, per Sprint 15,
  already runs 13 services plus a large dependency stack on one cluster.
* Single-node Raft storage is a single point of failure for secret availability until the documented
  HA upgrade is done; an outage of the Vault pod does not take down already-running services (CSI-sync
  produces ordinary K8s Secrets that persist independently), but blocks new pod scheduling / secret
  rotation while Vault is down.
* Moving DB credentials off the `docker` profile requires an in-cluster profile change
  (`docker` -> `prod`/`k8s`) that has not been audited for other profile-specific assumptions; that
  audit is explicitly deferred to Sprint 18 implementation, not resolved by this ADR.

---

## Alternatives Considered

### Kubernetes External Secrets Operator (ESO) instead of the Vault CSI provider

Rejected for this ADR's scope: ESO would still require Vault (or another backend) as the source of
truth, so it does not remove the need for this decision - it is an alternative *sync* mechanism,
functionally close to the CSI driver's `secretObjects` sync but with its own CRDs and reconciliation
loop. The CSI driver is chosen because it is HashiCorp's own supported integration path and keeps one
fewer moving part (no separate operator to run) for equivalent outcome at this scale.

### Vault Agent sidecar injector

Rejected: requires file-based or env-file-based secret consumption, which does not match this
platform's env-var-driven Spring configuration model without adding an entrypoint/init layer across
all 13 Dockerfiles. See Section 1.

### Multi-node HA Raft Vault with cloud auto-unseal from day one

Rejected for now, not forever: over-engineered relative to a single-cluster, 13-service, Kind-based
platform (Sprint 15's actual deployment target). Recorded as the explicit production upgrade path
rather than silently assumed.

### Bundle envelope encryption / key-id rotation into this ADR

Rejected: different blast radius (schema migration, converter rewrite, new starter) and different
owners (domain-engineer, platform-engineer) than standing up Vault (devops, security). Kept as a
named, explicitly out-of-scope follow-up per Section 3.

### Continue committing dev-default secrets and defer Vault indefinitely

Rejected: `deploy/helm/README.md` and `deploy/RUNBOOK.md` already flag the committed defaults as
DEV-ONLY and name Vault as the intended replacement; per-service DB credentials are additionally not
secreted at all today. Deferring leaves a real, previously-undocumented gap open.

---

## Supersession

This ADR supersedes `docs/architecture/security-posture.md` Section 5's "Production recommendation"
insofar as it concerns *where the AES key material is sourced from* (Vault, via the model in Section
1-2 above) - it does **not** supersede that section's recommendation on envelope encryption / key-id
rotation, which remains open per Section 3 above. It also resolves the "Real secrets from Vault/K8s
Secret" line item in Section 10's hardening checklist. `docs/product/TELCO-CRM-ADVANCED.md` Section
4.3's "Secrets, tokenization, and key management (Vault + HSM/KMS)" proposed-ADR line is satisfied by
this ADR for the Vault/secrets half; the HSM/KMS half remains future work per Section 3.

---

## Related ADRs

* ADR-010 Service Discovery and Configuration Strategy (config-server split this ADR preserves)
* ADR-011 Security Foundation (trust model this ADR's Kubernetes-auth-method policy scoping supports)
* ADR-017 Service Template Standard (per-service `ServiceAccount` this ADR relies on)
* ADR-021 PII and Data-Masking Strategy (the AES key this ADR relocates protects the data ADR-021
  governs the telemetry view of)
* ADR-026 Service Mesh and mTLS (sequencing dependency discussed in that ADR)
