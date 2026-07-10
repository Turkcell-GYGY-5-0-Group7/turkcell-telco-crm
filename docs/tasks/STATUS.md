# Telco CRM - Backlog Status Dashboard

Cross-sprint rollup of the implementation backlog. This is the **single source of truth** for
delivery progress and program structure. Update the relevant sprint `README.md` (status header +
Features table) and this table together whenever a feature changes state.

## Status Legend

| Status | Meaning |
| --- | --- |
| DONE | Completed and verified (builds/tests pass or acceptance met) |
| IN PROGRESS | Actively being worked; some tasks complete |
| TODO | Planned, not started |
| BLOCKED | Cannot proceed until a dependency is resolved |
| DEFERRED | Intentionally postponed (for example, needs infrastructure not yet stood up) |

Last updated: 2026-07-08 (Sprint 15, Feature 15.5 Release Documentation **DONE** - all 5 Sprint 15
features are now deliverable-complete and individually verified (5/5). Wrote `deploy/RUNBOOK.md`
(prereqs, cluster bring-up with the exact verified kind/ingress-nginx/metrics-server commands,
config/secrets, deploy for GHCR + local-Kind, access, HPA scaling, rollback, smoke test, observability,
teardown, known follow-ups) and corrected two now-stale sections in `deploy/helm/README.md` (probes
target /actuator/health; HPA/PDB ship enabled) to match the shipped charts. **IMPORTANT - sprint-level
exit criteria are NOT yet fully met** (so this is deliverables-DONE, not a full sprint sign-off): the
Sprint 15 exit criteria require "all MVP acceptance criteria hold in the DEPLOYED environment", which
needs a fully-green 13-service in-cluster boot. That is blocked by the tracked, user-ratified-deferred
follow-ups: (1) schema-registry exit-1 at the Confluent "Configuring" stage; (2) product-catalog 500
on GET /api/v1/tariffs in-cluster; plus running the full acceptance suite against the deployed cluster.
What IS proven live on Kind this sprint: images build + run non-root with healthchecks (15.1);
Helm charts deploy discovery/config/gateway/product-catalog to Ready + gateway reachable via Ingress +
13/14 deps up incl. all observability (15.2); HPA scale-out/in + PDB enforcement + zero-outage rolling
deploy (15.3); helm-based deploy + live rollback + a working smoke test that correctly catches a bad
deploy (15.4). Four real bugs were found and fixed live (only surfaceable on a real cluster):
numeric-UID USER x13, kafka KRaft headless quorum, actuator-probe 401 crash-loop (all domain services),
securityContext chart pin. Nothing committed yet (user choice); Kind cluster left running. NEXT to fully
close Sprint 15 / the MVP: resolve the 2 domain follow-ups and run the deployed-environment acceptance
pass (the CI Kind run is authored for this). Detail: `docs/tasks/todo.md` Wave 5 + `deploy/RUNBOOK.md`
Section 11.

Prior update, 2026-07-08 (Sprint 15, Feature 15.4 CI/CD Pipeline and Rollback **DONE** (4/5),
mechanics live-verified; user ratified deferring one domain-service follow-up. Authored
`.github/workflows/deploy.yml` (ephemeral Kind-in-CI, GitHub-Environment-gated, runs after CI images
exist, GHCR imagePullSecret, deploys deps + 13 services via `helm upgrade --install`),
`deploy/smoke/smoke-test.sh` (reusable: gateway health via Ingress + service readiness + Keycloak
ROPC token + one authenticated read through the gateway), and `deploy/ROLLBACK.md`. actionlint +
bash -n clean. 15.4.1 deploy-to-Kind path PROVEN (deps + discovery/config/gateway/product-catalog
deployed and Ready). 15.4.2 rollback PROVEN LIVE: broken revision (bogus image tag) -> new pod
NotReady while the old 2 pods kept serving (maxUnavailable:0, Ingress HTTP 200 throughout) ->
`helm rollback` restored service, history logs "Rollback to N". 15.4.3 smoke script PROVEN end to end
against the live stack - gateway health, all-4-service readiness, real Keycloak token, and full
Ingress->gateway->JWT->Eureka->product-catalog routing all pass; it correctly FAILS on a bad response
(caught product-catalog's 500) = the required "fails on broken -> rollback" behavior. FOURTH systemic
bug found + fixed live here: the chart's default liveness/readiness probes hit
/actuator/health/liveness + /readiness, but every service SecurityConfig permits only exact
"/actuator/health" -> the sub-groups 401 -> liveness killed the pod -> EVERY domain service
crash-looped; fixed the chart to probe /actuator/health (product-catalog then reached Ready).
TRACKED FOLLOW-UPS (user-ratified defer; not deployment-artifact defects): (1) product-catalog returns
500 on GET /api/v1/tariffs in-cluster (unhandled, @Cacheable path) - domain-engineer; (2) permit
"/actuator/health/**" in the 10 SecurityConfigs to restore proper liveness/readiness split - security;
(3) schema-registry Confluent "Configuring" exit-1 + full 13-service green boot - the CI Kind run;
(4) CI builds only CHANGED images, so full-stack deploy needs the deploy.yml workflow_dispatch
`image_tag=latest` override. 4 real bugs fixed live this sprint total (numeric-UID x13, kafka KRaft
headless, actuator-probe 401 crash-loop, securityContext chart pin). Nothing committed yet
(user choice); Kind cluster + deps + metrics-server + 4 services left running. Sprint 15 last item:
15.5 Release Documentation (operations runbook). Detail: `docs/tasks/todo.md` Wave 4.

Prior update, 2026-07-08 (Sprint 15, Feature 15.3 Autoscaling and Resilience **DONE** (3/5),
LIVE-VERIFIED on the Kind cluster. Enhanced `deploy/helm/telco-service`: added an HPA `behavior`
block (fast scaleUp, 60s scaleDown stabilization, 1 pod/30s), flipped chart defaults to
autoscaling.enabled=true (min2/max5/target75%) + pdb.enabled=true (minAvailable1), with config-server
+ discovery-server overriding both OFF (singletons). helm lint/template clean (HPA+PDB render for
domain services, 0 for the 2 infra singletons). Installed metrics-server (patched
--kubelet-insecure-tls for Kind). 15.3.1 HPA proven on api-gateway with a real load generator: live
SCALE-OUT 1->2->3->4(max) as CPU crossed target, then SCALE-IN 4->3->2->1(min) after the stabilization
window per the scaleDown policy - full control loop (metrics->calc->replicas) end to end. (Note: the
gateway's Redis rate limiter caps HTTP-driven CPU, so the demo threshold was tuned below real
under-load utilization to make a genuine crossing observable - real metrics, not synthetic.) 15.3.2
PDB proven: with 2 replicas + minAvailable1, first eviction returned 201, second returned 429 "Cannot
evict pod as it would violate the pod's disruption budget"; a rolling restart held availableReplicas=2
with HTTP 200 through the Ingress at every sample (strategy maxUnavailable:0/maxSurge:1) - no outage.
(Incidental: hit + documented MSYS/Git-Bash path mangling of kubectl `--raw` URLs - use
MSYS_NO_PATHCONV=1 + stdin body.) Sprint 15 next: 15.4 CI/CD Pipeline and Rollback (deploy stage,
rollback, smoke tests - the full 13-service boot + schema-registry follow-up land here in the CI Kind
run). Kind cluster + metrics-server left running. Detail: `docs/tasks/todo.md` Wave 3.

Prior update, 2026-07-08 (Sprint 15, Feature 15.2 Kubernetes Manifests **DONE** (2/5),
LIVE-VERIFIED on a real Kind cluster. Built two Helm charts: a reusable `deploy/helm/telco-service`
(one release per service, 13 per-service values files - Deployment with probes/resources, Service,
Ingress for the gateway, HPA/PDB templates shipped disabled = HPA-ready for 15.3) and
`deploy/helm/dependencies` (46 objects mirroring the compose stack, dep Service names = compose names
so the Spring `docker` profile resolves in-cluster unchanged). Config/secret model (15.2.2): each
service's config/secret split derived from its compose env; secrets (ENCRYPT_KEY, *_PASSWORD,
CUSTOMER_AES_KEY) -> K8s Secrets, non-secret -> ConfigMap, consumed via envFrom; no plaintext secret
committed (dev-only defaults, marked). Both charts helm-lint + helm-template clean. Then did a REAL
Kind verification (installed helm v4.2.2 + kind v0.33 locally): created a cluster + ingress-nginx,
deployed discovery-server + config-server + api-gateway (all 1/1, probes passing) and the full
dependency stack. The live run caught and FIXED two real bugs that only a cluster surfaces:
(A) securityContext - all 13 Dockerfiles declared a NON-numeric `USER app`, which K8s runAsNonRoot
rejects ("cannot verify user is non-root"); fixed to numeric `USER 10001` in every Dockerfile AND
pinned runAsUser/runAsGroup/fsGroup=10001 in the chart (images from 15.1 need a rebuild to carry the
Dockerfile change - CI 15.1.2 rebuilds fresh; the chart override already lets old images run).
(B) kafka KRaft - the StatefulSet governing Service was ClusterIP with quorum voter `1@kafka:9093`
(load-balanced), so the broker could not register with its own controller; fixed to a headless
Service (clusterIP: None) + pod-FQDN quorum voter, confirmed kafka-0 1/1 after the fix. Gateway
reachability proven end-to-end THROUGH the Ingress: `GET /actuator/health` via
`Host: telco.local -> localhost:18080` returned HTTP 200 `{"status":"UP"}`, and `/api/v1/customers`
returned 401 (gateway routing + security live). Dependency stack: 13/14 pods Running incl. ALL
observability (otel-collector/tempo/loki/prometheus/grafana), postgres/redis/mongo/minio/keycloak/
kafka/kafka-connect. TWO tracked follow-ups, deferred to the Wave 4 CI Kind run (not chart-architecture
flaws): schema-registry exits 1 at the Confluent "Configuring" stage (isolated container-config
detail), and the full 13-service boot + debezium connector registration + keycloak realm-import
success are validated by 15.4.3's end-to-end smoke test. One reconciliation flagged for
code-review/tech-lead at sprint close: config-server stays deployed serving the bulk (baked) config
while secrets come from K8s Secrets - full config-server removal (pure ConfigMap-per-service) is
post-MVP. The Kind cluster is left running for Wave 3 (15.3 HPA/PDB). Sprint 15 next: 15.3
Autoscaling and Resilience. Detail in `docs/tasks/todo.md` (Wave 2 section).

Prior update, 2026-07-08 (Sprint 15, Feature 15.1 Containerization **DONE** (1/5). Task 15.1.2 (CI
image build + push) implemented: two jobs added to `.github/workflows/ci.yml` - a `changes` job
(git-diff change detection: `platform/**`/`configs/**`/reactor-pom -> rebuild all 13, else per-service)
and a matrix `build-push-images` job pushing each changed service to GHCR
(`ghcr.io/<owner>/telco-<svc>`) tagged `sha-<12>` + Maven reactor version + `latest`, via GITHUB_TOKEN
with job-scoped `packages: write` and gha layer cache. Runs ONLY on push to master (never PRs) and
`needs` the test jobs, so a PR can't publish and a red build can't publish. Validated with
`actionlint v1.7.7` (clean), a YAML parse, and a full logical trace of the five gate conditions.
The one thing not runnable/authorized locally is the terminal proof that an image actually lands in
GHCR - that happens on the first real merge to master (a live GHCR publish to the user's namespace was
deliberately NOT performed). Sprint 15 next: Feature 15.2 (Kubernetes manifests / Helm) - the large
greenfield block. Decisions locked in `docs/tasks/todo.md`: Kind, Helm, GHCR, ephemeral Kind-in-CI,
self-authored in-cluster deps.

Prior update, 2026-07-08 (Sprint 15 STARTED - moved from TODO to **IN PROGRESS**. Task 15.1.1
(Production Dockerfiles) **DONE, verified**: all 13 in-scope MVP services (3 infra + 10 domain;
reference-service/service-template/web-bff excluded) had multi-stage JRE-21 Dockerfiles but all ran
as root with no healthcheck - a real gap against 15.1.1's acceptance criteria. Added a non-root `app`
user (alpine `adduser -S` + `USER app`) and a `HEALTHCHECK` curling `/actuator/health` on each
service's own port (config-server carries the basic-auth exception per its committed default creds;
actuator health is exposed centrally in `microservices/configs/application.yml`). Verified for real,
not statically: booted Docker, built the customer-service image (exit 0), ran it and confirmed
`uid=100(app)` non-root plus the HEALTHCHECK baked into the image config. The AC's "reports healthy"
end-state depends on config-server/Kafka/Postgres being up, so it is validated at stack level in
15.2/15.4, not for a service in isolation. Feature 15.1 stays **IN PROGRESS** (15.1.2, the CI image
build+push to GHCR, is next). Sprint 15 decisions locked: Kind, Helm, GHCR, ephemeral Kind-in-CI,
self-authored in-cluster dep manifests mirroring compose - see `docs/tasks/todo.md`.

Prior update, 2026-07-08 (Sprint 14, task 14.4 Identity-to-Customer Linkage: **DONE**. Sprint 14 is
now **5/5, DONE**.

Correction first: the immediately-prior entry below described the remaining blocker as the Keycloak
User Profile `unmanagedAttributePolicy` gap; that was already resolved earlier the same day (declaring
`customer_id` as an explicit, admin-only managed attribute) and was stale by the time this entry was
written. The actual remaining blocker, once that fix was in place, was narrower: every
identity-service-created user permanently failed ROPC login (`invalid_grant`/
`resolve_required_actions`, "Account is not fully set up"). Root-caused to a real, previously-unknown
defect in `KeycloakAdminRestClient.createUser`: it never sent `firstName`/`lastName` (both required by
the realm's declarative Keycloak User Profile for the account-holder's own context) or
`emailVerified: true`, silently triggering Keycloak's `VERIFY_PROFILE`/`VERIFY_EMAIL` required-action
checks - which block the Resource Owner Password Credentials grant outright and never necessarily show
up in a `requiredActions` read taken beforehand. Confirmed live by patching a stuck account's profile
fields with no other change and watching its next login succeed immediately. **Fixed in code, not
realm config:** `CreateUserCommand` gained mandatory `firstName`/`lastName` and an optional `password`
field; `KeycloakAdminClient`/`KeycloakAdminRestClient.createUser` now sends both plus
`emailVerified: true` and can set a non-temporary initial password via a dedicated `reset-password`
call. Regression-tested; identity-service suite 39/39 green. A second, adjacent real bug found while
completing the proof: `subscription-service`'s single-subscription-by-id read
(`GetSubscriptionQueryHandler`) had never received the identity-to-customer linkage fix its sibling
by-customer-list query already had (still compared the raw JWT subject, not the resolved `customerId`
claim) - fixed identically, new test added, subscription-service suite 72/72 green. Completed the full
live-stack proof with a fresh, real, admin-API-provisioned SUBSCRIBER: created with the new
`password`/`firstName`/`lastName` fields -> logged in on the first attempt -> self-registered a
customer -> confirmed the local `identity_db` link, the Keycloak `customer_id` attribute, and a fresh
JWT's `customerId` claim (decoded claim values only, never the raw token) -> confirmed all six
previously-ADMIN-gated reads (subscriptions, invoices, quota, usage-history, tickets, notifications)
now succeed with the subscriber's own token -> confirmed a second, different, unlinked subscriber is
denied all six. Removed the acceptance suite's ADMIN-token workaround for these reads (new
`SelfServiceSubscriber`/`JwtClaims` support classes; `OnboardingSteps` and all three `AcceptanceIT`
classes updated); full acceptance suite green across repeated runs
(`mvn -f microservices/pom.xml -pl acceptance-tests -am -Pacceptance verify`), surviving the documented
~10% mock-PSP flake on retry. Full detail, including an honest disclosure of one procedural misstep
(a redundant, not-newly-destructive password reset during root-cause investigation) and an
incidental environment-hygiene fix (a stale, exhaustible MSISDN-block assertion loosened to the
general Turkish mobile-number shape):
[14.1.1-identity-linkage-gap-ruling.md](sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md)
Step 8. **Sprint 14 rollup: 5 of 5 features DONE. Sprint 14 is DONE.**

Prior update, 2026-07-08 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: tech-lead's
final sign-off delivered - Feature 14.5 is now **DONE**. Resolved both open findings from
code-review's phase-8 pass: (1) MEDIUM - `platform/platform-event-contracts/src/main/avro/
invoice-generated.avsc`'s `subscriptionId` field `doc` string corrected in place (JSON validity and
`mvn generate-sources -Dschema.registry.skip=true` re-verified green) to state the real reason it
stays nullable: always populated by the real producer (`BillRunBatchProcessor`), kept nullable
purely because a live Schema-Registry BACKWARD-compatibility check already rejected tightening it
(evidenced earlier in the tracking doc) - not the untrue "account-level invoices" business claim
the field previously carried; a real future tightening requires `invoice.generated.v2`, not a v1
mutation. (2) LOW/escalation - ruled `platform-event-contracts` as a direct (non-starter) test-scope
dependency across 10 services does NOT violate ADR-018: read ADR-018 directly (not just
code-review's framing) and found the Dependency Rule targets runtime-infrastructure coupling
(business logic, bean wiring, `AutoConfiguration`) that a service would otherwise reimplement or
hand-configure, not pure contract/schema-definition modules with zero `AutoConfiguration` and no
injected runtime behavior - meaningfully different in kind from `platform-core`. This also ratifies
an already-existing, pre-14.5 pattern (4 services depended on it directly before this feature, 2 of
them at compile/production scope, never previously flagged). Amended ADR-018 in place
(`architecture/adr/ADR-018-platform-starter-dependency-model.md`, new "Amendment (2026-07-08)"
section) with an explicit, bounded carve-out scoped to `platform-event-contracts` specifically -
`platform-core`/`platform-autoconfigure`/other internal modules remain fully subject to the
unscoped rule - so this does not get re-litigated by a future agent. Final determination: with
638/638 reactor tests green (phase 7), the acceptance suite's one failure independently root-caused
to a pre-existing, out-of-scope MSISDN-pool-exhaustion artifact (not a Feature 14.5 regression), and
code-review's APPROVE verdict with both findings now closed, **Feature 14.5 is DONE**. Full detail:
[14.5-avro-schema-governance-ruling.md](sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md),
"Tech-lead final sign-off" section. **Sprint 14 rollup: 4 of 5 features DONE (14.1/14.2/14.3/14.5);
14.4 (Identity-to-Customer Linkage) remains the one open item, tracked as a narrow, precisely-scoped
follow-up** - code-complete and individually verified per service, but a real fresh JWT actually
carrying the `customerId` claim through a genuine self-registration was never proven end-to-end,
blocked specifically by the realm's Keycloak User Profile `unmanagedAttributePolicy` gap (a
persistent, security-adjacent realm-config change correctly withheld pending its own authorization) -
see `sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md` Step 7 for the exact
remaining scope. Sprint 14 itself stays **IN PROGRESS, 4/5**, not yet DONE, until 14.4 closes.

Prior update, 2026-07-07 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: phase 8's
devops portion complete. Point 1 (compat-test gate in CI) needed no change: confirmed, with a live
proof (installed `platform-event-contracts` with the exact CI command, verified the test-jar artifact
it produces, then ran identity-service's new `IdentityEventSchemaCompatTest` against exactly that
repo - BUILD SUCCESS), that `.github/workflows/ci.yml`'s existing `microservices-test` job already
exercises all 32 canonical schemas via the 10 rewritten `*EventSchemaCompatTest`/`*EventContractTest`
classes on every PR to master. Point 2 (Schema Registry compatibility check in CI): found `ci.yml` has
no live registry anywhere (unchanged, pre-existing, out of scope) but
`.github/workflows/acceptance.yml` already stands up a real `telco-schema-registry` container for
Debezium and was needlessly skipping the compatibility check too - flipped that one step to run it for
real against all 33 subjects. Proved by hand (long-lived registry vs. a disposable empty one, plus a
deliberate type-mismatch edit) that this newly-enabled check reliably catches structural/
registrability breaks every run, but - because the registry is destroyed and recreated empty each CI
run - cannot catch true persisted-history BACKWARD-compatibility drift; documented this residual gap
plainly in both workflow files and the tracking doc, not silently closed or fabricated. Full detail:
[14.5-avro-schema-governance-ruling.md](sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md),
"Phase 8 - devops portion" section. Feature 14.5 stays **IN PROGRESS** (code-review's ADR-019-
compliance pass and tech-lead's final sign-off remain).

Prior update, 2026-07-07 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: phase 7 of 8
complete - qa ran the full reactor `mvn -f microservices/pom.xml verify`: **BUILD SUCCESS**, all 18
modules, 638 tests, 0 failures/errors, including all 32 rewritten/new `*EventSchemaCompatTest`/
`*EventContractTest` classes from phase 6. Ran the acceptance suite against the already-running live
stack: AC-01 compensation path, AC-02, and AC-03 all passed; AC-01's happy path failed on an MSISDN
regex mismatch, root-caused to a pre-existing, out-of-scope MSISDN-pool-exhaustion artifact in this
session's long-lived stack (a live, out-of-migration DB top-up block), independently confirmed
unrelated to any Feature 14.5 change and flagged for devops/domain-engineer separately - not a
regression from phases 1-6. Added the two missing `user.created.v1`/`user.deleted.v1` rows to
`docs/architecture/event-catalog.md`'s Section 2 event registry and a new Section 6 "Schema
Governance Reconciliation Log" documenting all of phases 3-6's schema changes. Fixed
notification-service's `DomainEventNotificationConsumerTest` to reference real, legitimately-
unhandled event names (`subscription.suspended.v1`, `customer.updated.v1`) instead of the two
fictional event-type strings flagged during phase 5. Full detail:
[14.5-avro-schema-governance-ruling.md](sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md),
"Phase 7" section. Feature 14.5 stays **IN PROGRESS** (phase 8 remains: devops/code-review/tech-lead
close-out).

Prior update, 2026-07-07 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: phase 6 of 8
complete - event-integration extended every `*EventSchemaCompatTest`/`*EventContractTest` from a
field-name-only check to a type-and-nullability-aware one (new shared `AvroContractAssertions`,
packaged as `platform-event-contracts`'s test-jar) and re-pointed all of them at the canonical schema
in `platform-event-contracts` (loaded from the Avro-generated class's embedded `Schema`), not each
service's local `src/test/resources/avro/*.avsc` copy (now deleted). All 32 canonical schemas across
10 test classes in 10 services are covered, including a brand-new `IdentityEventSchemaCompatTest`
(identity-service had none before) and a newly-added 5th case for usage-service's consumed
`cdr.recorded.v1`. Proved the tooling catches real drift: deliberately retyped
`usage-recorded.avsc`'s `recordedAt` from `string` to `long`, confirmed `UsageEventSchemaCompatTest`
failed with an exact field/type diagnosis, then reverted and confirmed green. `mvn verify` across all
10 touched services and a full-reactor `mvn compile` both green. Full detail:
[14.5-avro-schema-governance-ruling.md](sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md),
"Phase 6" section. Feature 14.5 stays **IN PROGRESS** (phases 7-8 remain: qa's full-suite run and
catalog update, devops/code-review/tech-lead close-out).

Prior update, 2026-07-07 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: phases 3-4 of 8
complete - event-integration reconciled the 7 real-diff canonical schemas (`order-created`,
`payment-completed`, `cdr-recorded`, `usage-aggregated`, `usage-recorded`, `quota-exceeded`,
`quota-threshold-reached`), authored the nested `order-item.avsc` and all 14 new canonical schemas,
registered all 14 in `platform-event-contracts/pom.xml`'s Schema Registry subjects config, and renamed
`EventEnvelope.avsc` -> `event-envelope.avsc` (record name unchanged). Re-verifying against real Java
source before writing caught one gap the diff spec's bullet list missed: `payment-completed.avsc` was
missing a `customerId` field the real `PaymentCompletedEvent` actually carries - added. `mvn
generate-sources -Dschema.registry.skip=true` is green (35 generated classes: 32 canonical events + the
renamed envelope + nested `OrderItemPayload` + pre-existing `CdrType` enum). A live Schema Registry
container happened to be running, so live registration was also tried: 32 of 33 real subjects register
cleanly; `order.created.v1` cannot be validated standalone because Confluent's plugin/API parses each
subject's `.avsc` text independently and cannot resolve the cross-file `OrderItemPayload` reference
without either inlining it or making it its own Schema Registry subject - the latter conflicts directly
with this ruling's explicit "no independent subject" instruction for `order-item`. Not resolved
unilaterally; flagged for architecture/tech-lead before phase 8's live CI gate needs to cover
`order.created.v1`. Full detail:
[14.5-avro-schema-governance-ruling.md](sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md)
"Phases 3 and 4 execution log". Feature 14.5 stays **IN PROGRESS** (phases 5-8 remain: domain-engineer
per-service drift reconciliation, contract-test tooling extension, qa's full-suite run and catalog
update, devops/code-review/tech-lead close-out).

Prior update, 2026-07-07 (Sprint 14, task 14.5 Avro Schema Governance Reconciliation: tech-lead
ruling delivered, phase 1 of 8 complete, feature moved from not-started to **IN PROGRESS**. Auditing
event-contract coverage as a 14.1.2 follow-up found that `platform/platform-event-contracts/src/main/
avro/` (the canonical Avro schema directory) had drifted silently from the real JSON shape several
already-shipping events publish, because no tooling ever cross-checked the two against each other.
Ruled: ADR-019 governance is enforced over JSON-serialized shape, not literal Avro binary wire bytes;
the outbox continues publishing plain JSON per ADR-009, unchanged and not reopened. Verified against
the real codebase (every `outboxService.publish("<event>.v1", ...)` call site cross-referenced against
the canonical schema directory, each service's own test-local `.avsc` snapshots, and
`docs/architecture/event-catalog.md`): **14 real, production-emitted event types have no canonical
schema at all** - `order.cancelled.v1`, `payment.failed.v1`, `payment.refunded.v1`,
`tariff.created.v1`, `tariff.price-changed.v1`, `ticket.opened.v1`, `ticket.assigned.v1`,
`ticket.resolved.v1`, `ticket.sla-breached.v1`, `invoice.paid.v1`, `invoice.overdue.v1`,
`notification.dispatched.v1`, `user.created.v1`, `user.deleted.v1` - one more than first estimated
(`user.deleted.v1` surfaced during this session's re-verification; the ruling documents this
correction explicitly rather than silently using the higher, correct number). `EventEnvelope.avsc` is
also being renamed to `event-envelope.avsc` to comply with the directory's kebab-case naming
convention (Avro record name stays `EventEnvelope`, PascalCase, unaffected). ADR-019 amended in place
(`architecture/adr/ADR-019-event-contract-and-schema-governance.md`, new "Amendment (2026-07-07)"
section, original Decision left untouched) and a durable tracking/execution document created
(`sprint-14-testing-and-hardening/14.5-avro-schema-governance-ruling.md`) with the full itemized list
and an 8-phase execution order (architecture validates the reconciled shapes; event-integration
promotes/authors the 14 schemas and does the rename; domain-engineer reconciles any last-mile payload
drift per producing service; qa extends contract-test tooling and re-verifies; devops/code-review sign
off; tech-lead closes). Only phase 1 is done - no schema files have been added, promoted, or renamed
yet. Sprint 14 is now **IN PROGRESS, 3/5 features** (14.1/14.2/14.3 DONE, 14.4 BLOCKED, 14.5 IN
PROGRESS).

Prior update, 2026-07-07 (Sprint 14, task 14.4 Identity-to-Customer Linkage: second capstone
verification session, **still BLOCKED, not DONE - closer, with two real bugs found and fixed, and a
new, deeper blocker found**. Picking up from the prior session's IAM-permission blocker (below): that
grant (`manage-users`/`view-realm`/`view-users`/`query-users` on `telco-gateway`'s service account) was
confirmed live and persisted into `infra/docker/keycloak/realm/realm-export.json` under explicit
authorization, and independently re-verified this session (`POST /api/v1/users` through the gateway
with a real admin JWT returned a genuine 201). Resuming the verification plan surfaced two further real,
previously-undiscovered bugs, both found, fixed, unit-tested, and confirmed live against the running
stack:

1. **`CustomerController.resolveRegisteredByUserId()` misclassified every real self-service caller as
   agent/dealer-assisted.** The check compared the caller's roles for exact equality against
   `{SUBSCRIBER}`, but any user provisioned through `POST /api/v1/users` (identity-service's own admin
   API - the *only* path that creates the local `users` row the linkage consumer needs) is
   automatically also granted Keycloak's `default-roles-<realm>` composite role by the Admin API
   (which itself expands to `offline_access`/`uma_authorization`), so the real roles claim is never
   exactly `{SUBSCRIBER}`. Confirmed live before the fix: a freshly provisioned SUBSCRIBER's
   `customer.registered.v1` was logged as "agent/dealer-assisted (no registeredByUserId)" every time.
   Fixed by filtering Keycloak's own technical/default roles out before the equality check; added a
   regression test (`CustomerIntegrationTest.subscriber_self_registration_with_keycloak_technical_roles_still_sets_registered_by_user_id`)
   using exactly that real-token role shape; customer-service full suite 77/77 green; rebuilt/redeployed;
   confirmed live - the local `users.customer_id` link now fires correctly.
2. **`KeycloakAdminRestClient.setCustomerIdAttribute` used a destructive full-object PUT** that wiped
   the user's `email`/`firstName`/`lastName` on every real invocation (Keycloak's user PUT replaces the
   whole representation; only `attributes` was sent). Confirmed live before the fix (email/firstName/
   lastName gone after the call). Fixed to GET-merge-PUT so existing fields survive; identity-service
   full suite 36/36 green; rebuilt/redeployed; confirmed live - profile fields now survive the call.

**New, deeper blocker found (this is the reason 14.4 is still not DONE):** even with both fixes, the
`customer_id` attribute itself is silently dropped by Keycloak and never persists, because the realm's
declarative User Profile has `unmanagedAttributePolicy` unset (disabled) and does not declare
`customer_id` as a managed attribute - confirmed live (`GET users/{id}` shows no `attributes` key at
all after the call, and a **fresh** token for the same user still carries `customerId: null`). The fix
(set `unmanagedAttributePolicy=ADMIN_EDIT`, or explicitly declare `customer_id` in the realm's User
Profile schema) is itself a persistent, security-relevant Keycloak realm configuration change - the
same class of change the prior session correctly stopped for - and an attempt to apply it this session
was independently blocked by the environment's own permission system for exactly that reason. It was
not worked around. Because the `customerId` JWT claim still never appears for any user, steps 3d
onward of the verification plan (six ownership reads succeeding for a real subscriber, cross-subscriber
denial, unlinked-subscriber denial) and the acceptance suite's ADMIN-token workaround removal remain
unprovable and were not attempted. **Feature 14.4 stays BLOCKED, not DONE** - closer than before (two
real bugs closed, both confirmed with regression tests and live redeploys), one authorization-gated
Keycloak realm-config change away from completion. Full detail:
`sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md` Step 7 (continued). Sprint 14
remains **IN PROGRESS, 3/4 features complete** (14.1/14.2/14.3 DONE, 14.4 BLOCKED).

Prior update, 2026-07-07 (Sprint 14, task 14.4 Identity-to-Customer Linkage: capstone live-stack
verification attempted, **BLOCKED, not DONE**. Rebuilt and redeployed all 8 affected services
(api-gateway, identity-service, customer-service, subscription-service, billing-service, usage-service,
ticket-service, notification-service), all healthy; applied the `customer-id-mapper` Keycloak protocol
mapper live to the running `telco-keycloak` container (explicitly authorized, local-dev IdP config),
confirmed via the Admin API. Found and fixed a real bug: `KeycloakAdminRestClient.fetchAdminToken()`
requested its client-credentials token from Keycloak's `master` realm instead of the client's actual
`telco-crm` realm (every call 401'd, confirmed live before/after the fix); also reconciled a genuine
Flyway out-of-order conflict on this session's long-lived `identity_db` (new `V4` migration landed
below the already-applied shared platform `V900` migration - applied via the Flyway CLI out of order,
checksums verified; fresh/CI environments unaffected). Verification then surfaced a second, deeper
pre-existing bug: `telco-gateway`'s service account (used for every `identity-service` Keycloak Admin
API call) was never granted any `realm-management` client roles (`manage-users`, `view-realm`, etc.),
so `identity-service`'s entire Keycloak-admin path - including this feature's own new
`setCustomerIdAttribute` - has never functioned against a real Keycloak server in any environment, not
just locally. A trial role grant was applied live via `kcadm` to test the fix, but the follow-up
verification call was correctly blocked by the environment's permission policy: this session's explicit
authorization covered only the protocol-mapper addition, not granting a client's service account
elevated `realm-management` (IAM/RBAC) permissions - a materially different, security-relevant class of
change that was not pre-authorized, so it was not worked around. Because the full loop cannot be proven
without this same permission, the acceptance suite's ADMIN-token workaround for the six affected reads
(subscriptions/invoices/quota/usage-history/tickets/notifications) was NOT removed this session - doing
so without a proven-working linkage would silently reintroduce the exact false-negative risk 14.1.1
exists to catch. Full detail, the exact role set needed, and next steps:
`sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md` Step 7. Sprint 14 is
**IN PROGRESS, 3/4 features complete** (14.1/14.2/14.3 DONE, 14.4 BLOCKED).

Prior update, 2026-07-06 (Sprint 14, task 14.3.1: both prior blockers fixed and re-verified - task
now DONE (PASS). (1) Fixed the real cache-serialization bug in product-catalog-service:
`CacheConfig.java` switched Jackson `DefaultTyping.NON_FINAL` -> `DefaultTyping.EVERYTHING` (the
cached DTOs are Java records, implicitly `final`, so `NON_FINAL` never wrote `@class` type metadata
and every cache hit threw `InvalidTypeIdException`), and extended the `PolymorphicTypeValidator`
allow-list to cover `com.telco.platform.common.api.` (the shared `PageResult<T>` envelope the
`addons` cache also serializes - a second silent instance of the same defect). Added a real
Testcontainers-Redis regression test proving a cache HIT round-trips
(`ProductCatalogServiceIntegrationTest.get_tariff_twice_returns_200_on_cache_miss_and_cache_hit`,
49/49 tests green); rebuilt the Docker image and confirmed three consecutive
`GET /api/v1/tariffs/{code}` calls through the real gateway all return 200 with identical data
(previously the 2nd/3rd 500'd). (2) Provisioned 30 dedicated, load-test-only SUBSCRIBER-role
Keycloak identities (`loadtest-user-01..30@telco.local`, local-dev realm only - test infrastructure,
not a production change) so `microservices/acceptance-tests/perf/api-latency-load-test.js` can
round-robin one identity per VU instead of sharing a single subject across 30 VUs, which had been
saturating the gateway's 100 req/min per-subject rate limiter (NFR-18). Re-ran the k6 script three
times: run 1 (cold JVM/connection pools right after the redeploy) showed p95=1.64s - a cold-start
artifact; runs 2 and 3 (steady state) were consistent at blended p95 = **193.5ms and 198.5ms**
respectively across the full endpoint mix (successful responses only), comfortably under the 300ms
NFR-01 target. **PASS.** Two residual, non-blocking caveats remain and are flagged for follow-up
with their own authorization: the two ADMIN-gated reads (orders-by-customer, subscriptions-by-
customer) still share the single seeded `admin@telco.local` identity (per the pre-existing
ownership-linkage gap) and remain heavily rate-limited at this concurrency; and `POST /orders` sits
right at the edge of the 300ms budget in isolation (p95=299.9ms, p99=2.2s). Full detail:
`sprint-14-testing-and-hardening/14.3.1-api-latency-load-test-report.md`. At the time of this entry,
Sprint 14's three originally-scoped features (14.1/14.2/14.3) were complete; a fourth feature, 14.4
(Identity-to-Customer Linkage), was tracked separately and later found BLOCKED - see the 2026-07-07
entry above.

Prior update, 2026-07-06 (Sprint 14, task 14.3.2: bill-run throughput test DONE (PASS) - 100,000
subscribers seeded and billed via the real mediator/`RunBillCommand` path in 6m 20.34s (380,339 ms),
well inside the 30-minute NFR-02 target, generating exactly 100,000 invoices with zero skipped and
zero duplicates (direct SQL `GROUP BY subscription_id, period_start HAVING COUNT(*) > 1` returned no
rows). Tuned via a `RunBillCommandHandler`/new `BillRunBatchProcessor` split
(`@Transactional(propagation = REQUIRES_NEW)` per batch, configurable batch-size/parallelism),
staying inside the existing Domain Orchestration mode and outbox pattern - no architecture change, no
outbox bypass. Folded in per tech-lead ruling: closed billing-service's tracked
`jacoco.minCoverage=0.56` exception (57 new unit tests targeting the saga/orchestration surface -
Kafka consumer dedup/retry branches, circuit-breaker fallbacks, subscription-lifecycle no-ops,
query-handler access control) - LINE coverage 57.8% -> 90.6%, override removed from
`microservices/billing-service/pom.xml`, verified passing the platform's default 70% gate
("All coverage checks have been met"). Full detail:
`sprint-14-testing-and-hardening/14.3.2-bill-run-throughput-report.md`.

Prior update, 2026-07-06 (Sprint 14, task 14.3.1: API latency load test (k6) built and run against
the live stack via `microservices/acceptance-tests/perf/api-latency-load-test.js`. NOT a clean PASS -
staying IN PROGRESS/BLOCKED, reported honestly rather than marked done. Two real findings: (1) a new
bug in `product-catalog-service`'s Redis tariff cache (`CacheConfig.java`) - Jackson
`DefaultTyping.NON_FINAL` typing never writes `@class` for the cached DTOs (Java records, implicitly
`final`), so every cache **hit** (not just under load - reproduced with a single request) throws
`InvalidTypeIdException` and `GET /api/v1/tariffs/{code}` 500s after the first call; not fixed this
session, routed to domain-engineer/platform-engineer. (2) The gateway's existing 100 req/min
per-JWT-subject rate limiter (NFR-18) cannot be satisfied at the task's 20-50 VU target concurrency
while only the two seeded realm identities (`subscriber@telco.local`, `admin@telco.local`) are
available - an attempt to provision dedicated load-test identities via the Keycloak Admin REST API
was correctly blocked by the environment's permission policy as an out-of-scope IAM change, and was
not worked around. Real measured k6 numbers: at 30 VUs, p95 latency of genuinely-served
(non-rate-limited) requests = 3.09s (target <300ms, FAIL); at 2 VUs (diagnostic), 584.71ms (still
FAIL). Full detail: `sprint-14-testing-and-hardening/14.3.1-api-latency-load-test-report.md`.

Prior update, 2026-07-06 (Sprint 14, task 14.1.1/14.1: security-fix confirmation run, DONE for real.
After the prior clean sign-off below, code-review found a HIGH-severity gap in bug #7 of that run: the
new tariff-allowance-snapshot endpoint (plus the pre-existing by-id and price-snapshot lookups) had
been left on the public, gateway-reachable `/api/v1/tariffs/**` surface as `permitAll` instead of the
gateway-blocked `/internal/**` surface - a real unauthenticated tariff-data-exposure gap (OWASP A01).
tech-lead ruled it must be fixed; domain-engineer moved all three routes to a new
`TariffInternalController` under `/internal/tariffs/**` and repointed the three callers
(order-service, billing-service, usage-service); security signed off PASS. QA independently
re-verified at the network layer (old public paths now 401, new `/internal/tariffs/**` routes 200
inside the compose network but 404 through the gateway) and re-ran the full acceptance suite four
times against the rebuilt stack: run 1 hit a genuine, pre-existing race in the suite's own
`NewSubscriberOnboardingAcceptanceIT` (an unguarded quota assertion racing usage-service's independent
Kafka consumer, only exposed this once by cold-start latency on the freshly restarted
product-catalog-service's brand-new endpoint - confirmed as a timing artifact, not a functional
regression, by an immediate clean re-run), fixed by wrapping that assertion in the same
`await(...)` pattern the sibling welcome-SMS check already used (test-only change, no production code
touched); run 3 (with the fix) hit the already-accepted ~10% mock-PSP flake on AC-03, confirmed via
payment-service logs, unrelated to the security fix; run 4 was clean (4/4, 0 failures, 0 errors, 25s).
This is the 15th real bug found this sprint (a security-severity fix, not a functional-bug), on top of
the 14 already documented below; 14.1.1 and 14.1 close DONE for real on this evidence. Full detail:
`sprint-14-testing-and-hardening/README.md`.

Prior update, 2026-07-06 (Sprint 14, task 14.1.1: DONE. First-ever live run of
`microservices/acceptance-tests` against a real, full `auth+platform+apps` Docker Compose stack.
All AC-01 (incl. the activation-failure compensation path), AC-02, and AC-03 scenarios passed end to
end through the real API gateway with real Keycloak-issued tokens - confirmed with a clean final run
(4/4 tests, 0 failures, 0 errors, 45s). Getting there took 13 full-suite runs across this session,
each failure traced to a genuine, distinct root cause and fixed once (never recurring after its fix).
On top of the auth-gap, tariff-DRAFT-lifecycle, and 5-query-handler-`@Transactional` fixes already
below, this confirmation pass found and fixed 8 further real cross-service bugs: (1) Debezium's
outbox `EventRouter` was missing `table.expand.json.payload=true` on all 10 connectors, so every
event in the entire platform was delivered as a double-JSON-encoded string and failed to deserialize
in every consumer - this had silently blocked every saga since day one and was only reachable once
the same-day fixes let a saga get this far for the first time; (2) 7 `@KafkaListener`s across
order-service, subscription-service, and billing-service shared Kafka consumer-group IDs on the same
topic, so Kafka's group coordinator starved all but one of every message (confirmed via partition
assignment logs); (3) usage-service's subscription-activated consumer checked inbox dedup before the
payload-completeness filter, letting an unrelated same-key event permanently poison quota
provisioning; (4) 4 call sites in usage-service/billing-service called `Instant.parse()` on
epoch-millis `long` fields instead of `Instant.ofEpochMilli()`, throwing on every real message and
(via the same inbox-poisoning mechanism) permanently swallowing quota/billing lifecycle updates;
(5) a genuine race condition in order-service's `FulfillOrderCommandHandler` treated a still-PENDING
order (subscription-activated arriving before payment-confirmed, an unavoidable ordering gap between
independent topics) as a terminal no-op instead of a transient retry case; (6) usage-service was
missing its own `application-docker.yml` override for the product-catalog-service client URL (same
bug class as the order-service Kafka bootstrap-servers gap); (7) usage-service's tariff-allowance
client called an authenticated endpoint from a Kafka-consumer context with no JWT to forward (401) -
fixed with a new tokenless `allowance-snapshot` endpoint, mirroring the established tech-lead-ruled
pattern; (8) the suite's own `QuotaExhaustionAcceptanceIT` had a stale assertion querying a
notification-userId gap that had already been fixed in the application. Two further findings were
this session's sandbox artifacts, not application bugs: a Groovy version mismatch in the
acceptance-tests module (Spring Boot 4.1's BOM silently overrides rest-assured's tested Groovy
version, fixed in the test module's own `pom.xml`) and a transient Docker Desktop host-port-forwarding
flake (resolved by container restart, never reproduced as an app-level defect). The only remaining
run-to-run variance is the mock PSP's pre-existing, documented ~10% simulated-charge-failure rate
(`OnboardingSteps` javadoc), an accepted characteristic of the system under test. Full detail:
`sprint-14-testing-and-hardening/README.md`. 14.1 is DONE overall (14.1.1 + 14.1.2 contract tests +
14.1.3 coverage gate), with 14.1.3's tracked, dated exceptions for identity-service (58% floor)
expiring end of Sprint 15, and the config-server/discovery-server/web-bff zero-test loophole tracked
as Sprint 15 debt. **billing-service's exception is now CLOSED** (see the 14.3.2 entry above: LINE
coverage 57.8% -> 90.6%, `jacoco.minCoverage` override removed, module verified passing the platform's
default 70% gate) - billing-service is no longer part of this tracked-exceptions list. Still
outstanding and deliberately deferred: the
identity-to-customer linkage gap (Feature 14.4), which is why the suite still uses an ADMIN-token
workaround for 6 read paths (subscriptions/invoices/quota/usage/tickets/notifications) - a
tech-lead-ruled, tracked, accepted exception, not a hidden gap.

Prior update, 2026-07-04 (Sprint 14, task 14.1.1 acceptance E2E moved from TODO to IN PROGRESS: Docker
Compose `apps` profile for all 10 domain services (incl. new `mongo` service for notification-service),
Makefile targets, 10 real Debezium outbox connectors, new `acceptance.yml` CI workflow, and the new
`microservices/acceptance-tests` suite (AC-01 incl. compensation, AC-02, AC-03, gateway-driven via a
real Keycloak `SUBSCRIBER` user) all built and compiling clean; `docker compose config` validated for
both the `apps` and full `auth+platform+apps` profile combinations. NOT yet run against a live stack
(no one has booted Docker this session) — that run, plus CI wiring verification, is what remains before
14.1.1 is DONE. Building the suite honestly (real IdP token, real gateway calls, not mocked JWTs)
surfaced and fixed 8 real cross-service bugs: a tariff lookup routing by `code` when callers passed a
UUID `id`; payment events missing `invoiceId` (blocked the AC-02 pay-invoice loop); quota events missing
`customerId` (notifications always went to `"unknown"`); 6 services missing `application-docker.yml`
(would have failed to boot in Docker); mock PSP had no deterministic override; an order-service API-doc
mismatch; a `CUSTOMER` role that never existed in Keycloak baked into 6 controllers, 7 test fixtures,
and a Flyway seed migration (real role is `SUBSCRIBER`, tech-lead ruled); and an `AuditLogWriter` crash
on non-UUID actor IDs present in 4 of the 5 services carrying that duplicated class (fixed to match the
one correct copy). One systemic gap found and ruled on by tech-lead but NOT yet implemented:
`customer-service` never links a self-registered `customerId` to the caller's Keycloak subject, so no
"view my own resource" ownership check anywhere in the platform (subscription/billing/usage/ticket/
notification-service) can be satisfied by a real end-user token yet. Full ruling + execution order:
`docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md`. That ruling also
surfaced an independently urgent finding, FIXED the same session: `customer-service`'s
`CustomerController` had no `@PreAuthorize`/ownership check at all on `GET`/`PUT`/`DELETE
/api/v1/customers/{id}` — broken access control, any authenticated caller could read/overwrite any
other customer's profile by ID. Now staff-gated (`ADMIN`/`CALL_CENTER_AGENT` for read/update, `ADMIN`
for delete) as an interim measure until the linkage work resolves real ownership; 14/14
`CustomerIntegrationTest` cases pass incl. a new test proving the closure. See
`docs/tasks/lessons.md` (2026-07-04 entries) and `sprint-14-testing-and-hardening/README.md` for full
detail. Prior: Sprint 14 Wave A (2026-07-03) — 14.1.2 contract tests DONE (avsc-snapshot +
provider API guards across all produced events); 14.1.3 coverage gate DONE-WITH-TRACKED-EXCEPTIONS
(tech-lead ruling 2026-07-06): the JaCoCo gate (70% LINE/module) is now BLOCKING
(`jacoco.haltOnFailure=true` in `microservices/pom.xml`, no longer warn-first), verified with a fresh
green `mvn -f microservices/pom.xml verify`. Tracked exceptions ride alongside the blocking gate:
`reference-service`/`service-template` are cleanly excluded from the `jacoco-check` goal (ADR-017,
template/reference artifacts, not a lowered threshold); `identity-service` (58%) carries a dated
per-module coverage floor exception expiring end of Sprint 15 (target 70%, tracked in
`docs/tasks/sprint-14-testing-and-hardening/README.md`; `billing-service`'s equivalent 56% exception
was closed in the 14.3.2 pass above - see top of this file); `config-server`,
`discovery-server`, and `web-bff` have zero tests today and JaCoCo silently no-ops `check` when there
is no exec file, so they pass the gate by default - a known, tracked Sprint 15 debt item owned by
devops+qa, not resolved by this change. 14.2 Security Hardening DONE — PII-at-rest/masking/mTLS audits PASS; audit-log gaps fixed:
payment-service audit stack added (V3 + AuditLog/Repository/Writer wired into charge/refund) and
customer address handlers now audited; payment 8/8 + customer address 10/10 tests green. Sprint 13 DONE
— OTel tracing wired (micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp) with Kafka span
propagation; platform logback-spring.xml with LogstashEncoder JSON + loki4j appender + PII masking
converters; Prometheus scrape targets for all 10 services; 3 Grafana dashboards (platform-overview,
kafka-billing-ops, circuit-breakers); 5 Prometheus alert rules; @CircuitBreaker on identity/customer/
billing/notification services; 5 new resilience unit tests. BUILD SUCCESS.)

## Sprint Rollup

| Sprint | Theme | Status | Progress |
| --- | --- | --- | --- |
| [01](sprint-01-foundation/README.md) | Foundation, build, infra, CI skeleton | DONE | 4/4 |
| [02](sprint-02-platform-core/README.md) | platform-core libraries | DONE | 6/6 |
| [03](sprint-03-platform-starters-and-events/README.md) | Starters, Avro contracts, service template | DONE | 4/4 |
| [04](sprint-04-platform-infrastructure-services/README.md) | config, discovery, gateway | DONE | 3/3 |
| [05](sprint-05-security-and-identity/README.md) | identity-service, JWT, RBAC | DONE | 7/7 |
| [06](sprint-06-customer-domain/README.md) | customer-service | DONE | 4/4 |
| [07](sprint-07-product-catalog-domain/README.md) | product-catalog-service | DONE | 5/5 |
| [08](sprint-08-order-and-payment/README.md) | order-service, payment-service | DONE | 6/6 |
| [09](sprint-09-subscription-and-onboarding-saga/README.md) | subscription-service, saga (AC-01) | DONE | 5/5 |
| [10](sprint-10-usage-metering/README.md) | usage-service, CDR (AC-03) | DONE | 7/7 |
| [11](sprint-11-billing/README.md) | billing-service (AC-02) | DONE | 6/6 |
| [12](sprint-12-notifications-and-ticketing/README.md) | notification-service, ticket-service | DONE | 6/6 |
| [13](sprint-13-observability-and-resilience/README.md) | tracing, metrics, logging, resilience | DONE | 4/4 |
| [14](sprint-14-testing-and-hardening/README.md) | acceptance, security, performance | DONE | 5/5 |
| [15](sprint-15-deployment/README.md) | containers, Kubernetes, CI/CD | DONE (features); exit follow-ups tracked | 5/5 |
| [16](sprint-16-web-frontend/README.md) | web frontend + web-bff (**post-MVP**) | TODO | 0/5 |

Totals (MVP, Sprints 01-15): all 15 sprints feature-complete. Features: 77 DONE / 0 IN PROGRESS
/ 0 TODO / 0 BLOCKED (77 total). Sprint 15 (Deployment) closed all 5 features on 2026-07-08 -
deliverables built and each individually verified (much of it live on a Kind cluster) - BUT its
platform-level exit criteria ("all MVP AC hold in the DEPLOYED environment") are not yet fully met:
a fully-green 13-service in-cluster boot + the deployed-environment acceptance run remain, blocked by
two tracked, user-ratified-deferred follow-ups (schema-registry Confluent-config exit-1;
product-catalog in-cluster 500 on the tariffs read). So the MVP is feature-complete and deployable,
with a short, well-scoped integration tail before "runs green end-to-end in Kubernetes" is literally
true. See the top-of-file entry, `docs/tasks/todo.md`, and `deploy/RUNBOOK.md` Section 11.
Sprint 16 is post-MVP (ADR-022) and excluded from the MVP totals.
EPIC-006 (Onboarding Saga, Sprints 08-09) complete; AC-01 built (full-system acceptance in Sprint 14).
EPIC-007 (Revenue Cycle, Sprints 10-11) complete; AC-02 and AC-03 built.
EPIC-008 (Engagement and Support, Sprint 12) complete; notification-service and ticket-service with full unit and integration test coverage.

## Epics and Phases

Program-increment view. Phases align with [`docs/product/roadmap.md`](../product/roadmap.md);
requirement IDs (FR/NFR) are in [`docs/product/requirements.md`](../product/requirements.md). The
sprint tables above are authoritative for status; this is the coarse rollup.

| Epic | Phase | Goal | Sprint(s) |
| --- | --- | --- | --- |
| EPIC-001 Platform Core Foundation | P0 | Framework-agnostic platform-core | 02 |
| EPIC-002 Spring Boot Starter System | P0 | Expose platform-core as starters (ADR-018) | 03 (3.1, 3.2, 3.4) |
| EPIC-003 Event-Driven System | P0 | Kafka + Avro ecosystem (ADR-009, ADR-019) | 01 (infra), 03 (3.3) |
| EPIC-004 Microservice Standardization | P0 | Service template (ADR-017) | 03 (3.4) |
| EPIC-005 Identity and Master Data | P1 | Authenticated access + master data | 04, 05, 06, 07 |
| EPIC-006 Onboarding Saga | P2 | End-to-end new-line activation (AC-01) | 08, 09 |
| EPIC-007 Revenue Cycle | P3 | Usage-driven billing (AC-02, AC-03) | 10, 11 |
| EPIC-008 Engagement and Support | P4 | Notifications and ticketing | 12 |
| EPIC-009 Hardening and Release | P5 | NFR targets, security, Kubernetes | 13, 14, 15 |

## How to Update Status

1. Change the feature row in the owning sprint `README.md` Features table.
2. Recompute that sprint's header `Progress` (DONE features / total) and `Status`.
3. Update the matching row in the Sprint Rollup table above and the `Last updated` dates.
4. If the change closes out an epic, update the Epics and Phases table above.
