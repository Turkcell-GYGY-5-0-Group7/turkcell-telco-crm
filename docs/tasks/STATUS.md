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

Last updated: 2026-07-18 (Sprint 22 Invoice Dispute/Chargeback - **Feature 22.6 (event registration +
ticket-service integration + cross-service test suite) closes the sprint at 6/6, code-complete**.
Six `dispute.*.v1` events registered as governed Avro contracts (ADR-019), `dispute-outbox-connector.json`
added (closing the real gap flagged at the end of 22.4/22.5 - `dispute.events` was never actually
producible before this), and a new `DisputeOpenedTicketConsumer` auto-opens a `DISPUTE`-category ticket
reusing ticket-service's existing SLA machinery (`OpenTicketCommand`/`Ticket.open(...)` extended via an
additive overload - the ~15 existing call sites needed zero changes, confirmed by Grep before and after).
The sprint's own Exit Criteria's cross-service proof (`billing-service`/`payment-service
DisputeConsumersIntegrationTest`, `DisputeResolutionAcceptanceIT`) is written and compile-verified only,
per this sprint's standing no-Docker constraint - never executed, deferred to the next Docker-available
session. **A significant correction surfaced during this close-out pass**: the "JaCoCo 70% gate met"
claims recorded for Features 22.4/22.5 (below) and repeated in this session's earlier entries were based
on an incremental `mvn verify` that silently merged in `jacoco.exec` coverage data left over in
`target/` from a prior Docker-available run, inflating the reported percentage. A genuine `mvn clean
verify` with the same Docker-gated-test exclusions shows the gate does **not** actually pass
Docker-free-only for billing-service (61.3%) or payment-service (54.1%) - a pre-existing property of
those two services' coverage profile (their Docker-gated integration/perf tests carry real coverage
weight that no Docker-free unit test replaces), not a Sprint 22 regression. dispute-service (73.2%) and
ticket-service (88.1%) were independently confirmed to genuinely meet the gate on a clean build. All
Docker-free *test-pass* counts throughout this sprint remain accurate; only the coverage-gate claims for
billing-service/payment-service were wrong. Detail: sprint-22 README's new "22.6 Build and Verification
Record" section, which also corrects the 22.4/22.5 record in place.

Prior update, 2026-07-17 (Sprint 22 Invoice Dispute/Chargeback - **Features 22.4/22.5 (billing-service
and payment-service dispute extensions) built this session on top of 22.1-22.3 (5/6 total); Feature
22.6 (ticket-service integration + cross-service tests) remains TODO, correctly last since it depends
on 22.3/22.4/22.5 all being done**. A genuine, non-obvious finding drove this session's design: two
Explore agents disagreed on which inbox-dedup pattern is real in this codebase (manual
`InboxService.firstSeen` vs. `IdempotentRequest`/`InboxBehavior`) - resolved by reading the actual
consumer source directly rather than trusting either summary, revealing that **both patterns are
genuinely real, split by service**: billing-service still uses the manual `firstSeen(messageId,
handler)` path; payment-service was deliberately refactored to the `IdempotentRequest` pipeline path
(confirmed via each service's own existing consumer javadocs and by reading `InboxBehavior.java`
directly - its dedup key is `(idempotencyKey(), request.getClass().getName())`). Each of the six new
consumers (three per service) follows its own service's established convention; using the wrong one
anywhere would have been a real, easy-to-miss bug. A second correctness question was resolved before
writing any consumer: Debezium's `EventRouter` sets the Kafka record key to `aggregate_id` (=
`disputeId`, constant across all six dispute event types per ADR-028 Section 6's own ordering
guarantee) - verified safe to reuse as the dedup id anyway, since each event type has its own
dedicated consumer/command and fires at most once per dispute in the state machine. **22.4**: `Invoice`
gained `disputeStatus` (hold flag, unconditional flip) and `applyDisputeAdjustment(amount)`
(check-then-act: no-ops unless `ON_HOLD` - the ratified ADR-028 amendment's required second line of
defense), a new additive `InvoiceLine.of(...)` overload with a `lineType` param (old 4-arg factory
delegates to it, zero existing call sites touched), three commands/handlers, and three Kafka consumers
mirroring `SubscriptionSuspendedBillingConsumer`'s manual-inbox shape; the overdue/dunning query now
excludes `ON_HOLD` invoices. **22.5**: `Payment` gained `disputed` (unconditional flip, no PSP call, no
status change), the two retry-selection repository queries now filter `disputed = false` (a bonus
effect: this also correctly suppresses permanent-failure expiry while disputed), two `IdempotentRequest`
commands, and three Kafka consumers mirroring `OrderCreatedEventConsumer`/
`SubscriptionActivationFailedEventConsumer`'s pipeline shape - the customer-resolved consumer
dispatches the **existing, unmodified** `RefundPaymentCommand` (diff-verifiable, zero changes to
`RefundPaymentCommandHandler.java`), with a read-side no-op guard mirroring
`SubscriptionActivationFailedEventConsumer`'s exactly and `Payment.markRefunded()`'s existing guard as
the second line of defense. **Live-verified this session** (no Docker needed): both modules compile
clean, `dependency:tree` re-confirms zero `platform-core` in either graph (ADR-018, no new deps
added), and full `mvn verify` on both - excluding only each service's own pre-existing, already-Docker-
gated tests (confirmed failing purely on "Docker environment not found," unrelated to this session) -
**billing-service 91/91 green** (its entire suite, not just the new tests), **payment-service 55/55
green** (same), both package to a valid jar. (The "JaCoCo 70% gate met on both" claim originally made
here was corrected in the 2026-07-18 update above - see that entry.) One real test bug (not a
production bug) was found and fixed during this session's own verification: a
`DisputeResolvedCustomerPaymentConsumerTest` assertion compared the wrong UUID variable; fixed and
re-verified green. **NOT verified live** (needs Docker/a live Kafka cluster): an actual Kafka round
trip for any of the six new consumers. Also flagged: `infra/docker/kafka-connect/connectors/` has no
`dispute-outbox-connector.json` yet - a genuine, separate infra gap meaning `dispute.events` is never
actually produced end to end regardless of Docker availability, flagged for Feature 22.6 or a
`devops`/`event-integration` follow-up. Nothing committed yet (user choice). Detail: sprint-22
README's new "22.4/22.5 Build and Verification Record" section.

Prior update, 2026-07-17 (Sprint 22 Invoice Dispute/Chargeback - **Feature 22.3 (Dispute API +
evidence upload) built this session on top of 22.1/22.2 (3/6 total); Features 22.4-22.6 remain TODO**.
22.3 added `GetDisputeQuery`/`GetDisputesByCustomerQuery` + handlers (both
`@Transactional(readOnly = true)` - load-bearing, since the response DTO touches lazy
`@OneToMany` collections and `open-in-view` is platform-wide `false`), MinIO evidence storage
(`DisputeEvidenceStorage`/`MinioDisputeEvidenceStorage`/`MinioConfig`, mirrors customer-service's KYC
adapter exactly, reuses the already-shared `minio` resilience4j instance), `DisputeController`
(`/api/v1/disputes`: open/evidence-upload/evidence-download-url/resolve/withdraw/get/list),
`DisputeSecurityConfig`/`DisputeAccessDeniedAdvice` (verbatim copies of order-service's), and
`docs/api-contracts/dispute-service.md`. A real, pre-existing bug in a sibling service was found and
deliberately not replicated: ticket-service's `@PreAuthorize("hasRole('ADMIN') or
hasRole('SUPPORT')")` references a `SUPPORT` role that does not exist in the Keycloak realm
(canonical roles per `docs/architecture/keycloak-and-auth.md`: `SUBSCRIBER, CALL_CENTER_AGENT,
DEALER, MARKETING_MANAGER, BILLING_OPERATOR, ADMIN, SERVICE`) - dispute-service's agent-facing
`/resolve` endpoint uses the real `CALL_CENTER_AGENT` role instead; fixing ticket-service's own bug
was out of this sprint's scope. Phase 1's `OpenDisputeCommand`/`SubmitEvidenceCommand`/
`WithdrawDisputeCommand` (and their handlers/tests) were retrofitted with `callerCustomerId`/
`callerIsAdmin` fields and now 403 via `AccessDeniedException` when a non-admin caller acts on
someone else's dispute - required by 22.3.3's own acceptance criteria, and correctly compared
against the caller's own linked customer-service id (`UserContext.customerId()` via
`CurrentUserProvider`), not the raw Keycloak subject, since `Dispute.customerId` and a Keycloak
subject are different id spaces (order-service's `Order.userId`-as-owner model doesn't transfer
directly here). List-by-customer uses the "silently scope, don't 403" style instead, matching
order-service's own list-endpoint convention. **Live-verified this session**: `mvn ... -am compile`
clean with the two new deps (`io.minio:minio`, `springdoc-openapi-starter-webmvc-ui`, both
version-managed centrally, no explicit version needed); `dependency:tree` re-confirms zero
`platform-core` (ADR-018); full `mvn ... verify` - **84/84 tests green**, JaCoCo 70% line-coverage
gate met (required two added handler-level tests after an initial 69% miss), `package` produces a
valid jar. **NOT verified live** (needs Docker, deferred): a real multipart upload against a real
MinIO instance, a real `@PreAuthorize`/`SecurityFilterChain` integration test against a real JWT
(`DisputeController` itself has no direct `@WebMvcTest` - covered only transitively via the
handler/query tests it dispatches to), and actual service startup/`/actuator/health`. Nothing
committed yet (user choice). Detail: sprint-22 README's new "22.3 Build and Verification Record"
section, `docs/api-contracts/dispute-service.md`.

Prior update, 2026-07-17 (Sprint 22 Invoice Dispute/Chargeback - **ADR-028 ratified (Proposed ->
Accepted) and Features 22.1/22.2 built this session (2/6), scoped to this session by explicit user
choice - Features 22.3-22.6 remain TODO**. Branch `feature/sprint-20-chaos-experiment-library` (no new
branch created this session). Before any code: an `architecture` agent validated ADR-028 against
ADR-004/006/009/017/019/021 - verdict "approve with amendment," no redesign - and a `tech-lead` agent
ratified it, applying four amendments in place: (1) Section 5 now states explicitly that
payment-service's refund reuse is an internal `Mediator` dispatch inside its own inbox consumer, never
a synchronous cross-service HTTP call from dispute-service (closes an ADR-006 misreading risk); (2)
Section 5 now requires billing-service's future `ApplyDisputeAdjustmentCommandHandler` (22.4.3) to be
check-then-act (`Invoice.disputeStatus == ON_HOLD`, no-op otherwise) as a second line of defense against
a duplicate financial adjustment if inbox dedup is ever bypassed - payment-service's mirror path already
gets this for free from `Payment.markRefunded()`'s existing guard, billing-service's didn't; (3)
Section 4's ambiguous 3-line ASCII state diagram was replaced with design-note.md's unambiguous tree
form (the two documents were never in actual disagreement, only ADR-028's rendering was unclear); (4)
Section 6 now states explicitly that all six `dispute.*.v1` events use `aggregate_id = disputeId`,
load-bearing for the per-dispute Kafka ordering the provisional-hold invariant depends on. Separately
flagged (not fixed, pre-existing and unrelated): `docs/architecture/service-catalog.md`'s
audit-mandated list omits order-service despite order-service shipping its own `audit_log` table -
recommended for a future reconciliation pass, out of this sprint's scope. **22.1**: scaffolded
`microservices/dispute-service/` (port 9012, Domain Orchestration, parent `domain-services-parent` +
`starter-mediator` only, matching payment-service's shape rather than service-template's) - pom.xml,
Application class, application.yml, `microservices/configs/dispute-service/application.yml`, Dockerfile
(Sprint 15 pattern), README.md/CLAUDE.md; registered the module in `microservices/pom.xml` and added a
new Section 6 (Post-MVP Services) to `docs/architecture/service-catalog.md` with a dispute-service row
(the catalog's Sections 1-5 are explicitly MVP-scoped, so a new section was added rather than mutating
those tables' own stated scope). Flyway migrations for `disputes`/`dispute_evidence`/
`dispute_state_history` (design-note.md Section 7 exact field list) and `audit_log` (mirrors
payment-service's V3 exactly). Structural JPA `Dispute`/`DisputeEvidence`/`DisputeStateHistory`/
`DisputeStatus`/`AuditLog` (framework-free, `Order.java`/`OrderItem.java`-style private-ctor +
static-factory, `DisputeStateHistory` modeled as a true JPA child entity since no existing
`*StateHistory*` analogue exists anywhere in this codebase) plus four Spring Data repositories.
**22.2**: full `Dispute` state machine (`beginReview`/`submitEvidence`/`resolveCustomer`/
`resolveMerchant`/`withdraw`/`close`) resolving the state diagram's `EVIDENCE_SUBMITTED -> UNDER_REVIEW`
loop by making `beginReview()` legal from both `OPENED` and `EVIDENCE_SUBMITTED` (confirmed correct by
the exact task-spec math: 7 states x 6 methods = 42 legal+illegal cases, matching `DisputeStateMachineTest`'s
own stated count precisely) - each transition appends one `DisputeStateHistory` row via a private
`transitionTo(...)` helper. Six commands/handlers (`Open`/`SubmitEvidence`/`ResolveDisputeCustomer`/
`ResolveDisputeMerchant`/`Withdraw`/`Close`), `AuditLogWriter` (mirrors payment-service's exactly), six
frozen `dispute.*.v1` event DTOs (ADR-028 Section 6/design-note.md Section 8's exact field lists) -
each handler follows `RefundPaymentCommandHandler`'s exact load -> domain-transition -> save -> audit ->
`OutboxService.publish("dispute", disputeId, eventType, payload)` shape, no `@Transactional` (Mediator's
`TransactionBehavior` wraps it), no direct Kafka call, no write to `billing-db`/`payment-db` anywhere -
the provisional-hold invariant (ADR-028 Section 5) is upheld structurally, not just by convention.
**Live-verified this session** (no Docker needed for any of this): full `platform` reactor install
clean; `dispute-service` module `-am compile` clean; `dependency:tree` confirms zero `platform-core` in
the graph (ADR-018); full `mvn ... verify` - **66/66 tests green** (48 `DisputeStateMachineTest` cases +
18 Mockito-based handler tests across all six handlers, happy-path + illegal-transition/not-found
rejection each), JaCoCo 70% line-coverage gate met ("All coverage checks have been met"), and `package`
produces a valid Spring Boot fat jar. **NOT verified live** (needs Docker, deferred to next session):
`DisputeRepositoryTest` (`@DataJpaTest` + Testcontainers round-trip persistence for all three entities,
written to the same standard as `OrderRepositoryTest` but not run); actual service startup, Eureka
registration, and `/actuator/health` returning `UP` (22.1.1's own stated acceptance criteria). Nothing
committed yet (user choice, matches this repo's established pattern). Detail: sprint-22 README's
Features table and new "22.1/22.2 Build and Verification Record" section, ADR-028 (ratification notes
in Sections 4/5/6), `docs/architecture/service-catalog.md` Section 6.

Prior update, 2026-07-14 (Sprint 20 Chaos Engineering - **all 5 features authored this session
(5/5), zero live-verified** - a genuinely different completion shape than most prior sprints, so
read carefully before treating this as "done". Built on branch `feature/sprint-20-chaos-experiment-library`
(new, off `master`; Sprint 19 - see the entry directly below - was confirmed already merged via
`git log`, PR #29, contradicting that entry's own "nothing committed yet" text, itself a live
example of the 2026-07-13 lessons.md rule about not trusting a stale claim without checking). No new
ADR (tech-lead ruling, extends ADR-012/ADR-013, per the sprint README). **20.1**: `deploy/chaos/`
Chart.yaml/Chart.lock/charts/chaos-mesh-2.8.3.tgz (first repo chart to vendor an upstream dependency
via `dependencies:`+`Chart.lock`, mirroring `deploy/helm/vault`'s existing precedent - not the
self-authored-template shape of `deploy/helm/dependencies`), values.yaml (telco-namespace scoped,
pinned image tags, `dashboard.create: false`, containerd runtime override for Kind), README.md
(install/CRD-verification/dashboard-decision docs). Live-verified for real before Docker died:
`helm dependency update`, `helm lint`, `helm template`, and two `helm upgrade --install` runs
(chart deployed=true both times); `chaos-daemon` confirmed `2/2 Running`. NOT verified:
`chaos-controller-manager` reaching `Running` (last seen `Pending`/`Insufficient memory` - a
pre-existing, unrelated leftover Kind cluster from an earlier session was already at ~99% node
memory with 13 services + deps mid-reschedule after its node container had been stopped and
restarted) and the CRD-registration checks (20.1.2) - Docker Desktop itself then became unresponsive
(`500 Internal Server Error` / connection timeouts on `docker info`/`docker ps`, `wsl -d
docker-desktop` unreachable) and did not recover for the rest of this session despite repeated
polling. **20.2**: `deploy/chaos/STEADY-STATE.md` - hypothesis/dashboard/panel/alert mapping table,
pre-flight dashboard-reachability section, baseline PromQL queries, all citing real, verified values
(not the README's loose phrasing) - and explicitly corrects two inaccuracies found in the sprint's
own source docs: (1) `platform-overview` has no p99 latency panel, only p95 ("HTTP p95 Latency by
Service (s)"); (2) the README/20.3 task file's assumed `order-service -> payment-service`
Resilience4j pairing does not exist (that link is Kafka-only/async) - the real pairing is
`order-service -> customer-service` (the only two breakers order-service's `ResilienceConfig.java`
actually registers are named `customer-service` and `product-catalog-service`), and there is no
`slowCallDurationThreshold` configured, so the breaker trips via the failure-rate path, not a
slow-call path. Documentation-only, no live cluster needed - fully authored, no live gap. **20.3**:
`deploy/chaos/experiments/{pod-kill-order-service,latency-order-to-customer,
partition-billing-service-kafka}.yaml` (the second file renamed from the task's original
`latency-order-to-payment.yaml` per the 20.2 correction above), each with a bounded `duration`,
header hypothesis/abort-command comments, and `selector.namespaces`/`target.selector.namespaces`
hard-set to `["telco"]` only; selectors grounded in the real Helm chart label conventions
(`deploy/helm/telco-service/templates/_helpers.tpl`, `deploy/helm/dependencies/templates/kafka.yaml`)
and the real `outbox_event` table (`starter-outbox`'s `V900__platform_outbox.sql`), not invented.
Per this repo's lessons.md rule (2026-06-23, propagate a corrected assumption to its source, not
just the deliverable), subtask 20.3.2 and the README's Feature 20.3 note were corrected in place to
match the real pairing. A genuine new finding surfaced and documented (not silently papered over):
`order-service`'s `customerRestClient` bean has no configured connect/read timeout at all, so a
delay-only `NetworkChaos` fault may not reliably produce failures for the breaker to count - flagged
in the manifest header for live investigation, not assumed to work. Entirely authored, zero
`kubectl apply` runs - Docker was down for this feature's whole session. **20.4**:
`deploy/chaos/GAMEDAY-RUNBOOK.md` (prerequisites + one subsection per experiment with copy-paste
apply/dashboard/abort steps, sourced from 20.1-20.3's real outputs) plus a post-game-day findings
template (explicitly marked unfilled/example-only - no fabricated results) and a two-line
cross-link added to `deploy/RUNBOOK.md` Section 10 (Observability - corrected from the task files'
assumed Section 9, since Sprint 15.5's runbook has grown to 15 sections and Observability is
actually Section 10). Documentation only; explicitly flagged in the file itself that none of its
commands have been dry-run against a live cluster yet. **20.5**: real, not assumed, RBAC finding -
extracted the vendored `chaos-mesh-2.8.3.tgz` and read its actual `controller-manager-rbac.yaml`
Go templates rather than accepting the task file's "likely cannot be namespace-scoped" assumption:
the fault-injection permission set (pods/configmaps/secrets/chaos-mesh.org CRs - the one that
matters) CAN be namespace-scoped via the chart's own `clusterScoped`/`controllerManager.targetNamespace`
values, so `deploy/chaos/values.yaml` was updated to set `clusterScoped: false` and
`controllerManager.targetNamespace: telco` - closing a real gap rather than only documenting it as
residual risk. One permission set (read-only node/PV/PVC watch + SAR create) is irreducibly
cluster-wide by the chart's own unconditional `ClusterRoleBinding` template - documented as accepted
residual risk (read-only, no fault-injection capability, and every experiment's own selector is
`telco`-only regardless). 20.5.2's guardrail checklist and 20.5.3's manual-only/CI-untouched grep
checks were both run for real (`grep -rl "kind: Schedule\|kind: Workflow" deploy/chaos/*.yaml
deploy/chaos/experiments/*.yaml deploy/chaos/values.yaml` and `grep -rl "deploy/chaos"
.github/workflows/`, both empty as required). Deferred to a live cluster: the `kubectl auth can-i
--list` confirmation and the `helm template` render check proving a `RoleBinding` (not
`ClusterRoleBinding`) actually renders - the conclusion is from reading the chart's raw template
source, not a live render, since `helm` dropped out of this session's `PATH`-augmented shell once
Docker died mid-session. **Overall**: this sprint is **feature-complete in authored form** but
**none of its live-cluster exit criteria are proven** - a pod actually being killed and rescheduled,
a breaker actually tripping, a partition actually healing with zero lost `outbox_event` rows, and
dashboards actually rendering live data are all open follow-up work for the next session with a
healthy Docker Desktop. Committed as commit `128a678` (working tree clean, branch up to date with
`origin`) - the "nothing committed yet" language in earlier drafts of this entry was stale; see the
2026-07-17 documentation-sync note below. Detail: sprint-20 README's Features table
and Feature notes, `deploy/chaos/README.md`, `deploy/chaos/STEADY-STATE.md`,
`deploy/chaos/GAMEDAY-RUNBOOK.md`.

Prior update, 2026-07-14 (Sprint 19 Service Mesh and mTLS - Features 19.3 and 19.4 authoring and static
verification complete, plus Feature 19.5's diff-only subtask, session continued from 19.1/19.2 (DONE,
prior sessions, uncommitted). **19.3**: authored
`deploy/helm/telco-service/templates/{server,authorizationpolicy,meshtlsauthentication}.yaml` (one
Linkerd `Server` per service; `AuthorizationPolicy` + `MeshTLSAuthentication` restricting inbound to the
`api-gateway` mesh identity by default, gated on `meshPolicy.enabled`) and per-service
`deploy/helm/values/*.yaml` overrides: `api-gateway` (`meshPolicy.enabled: false` - its real caller,
ingress-nginx, is unmeshed), `config-server`/`discovery-server` (`authorizedClients` widened to all 13
services, their real caller set), and `customer-service`/`order-service`/`product-catalog-service`
(widened for their one-to-three real non-gateway synchronous callers). An Explore-agent audit grepped
every service for cross-service `RestTemplate`/`WebClient`/`RestClient` usage (no `@FeignClient` exists
in this repo) and confirmed the three overrides account for all five real cross-service HTTP calls in
the codebase - no missing override. 19.3.3's confirmatory audit confirmed `GatewaySecurityConfig`'s
`/internal/**` edge-deny is untouched by this sprint (zero diff under `microservices/api-gateway/`) and
structurally cannot be bypassed by the new mesh policies (mTLS-identity layer, no HTTP-path matching).
**19.4**: a devops agent authored the default-deny `NetworkPolicy` baseline
(`deploy/helm/dependencies/templates/networkpolicy-default-deny.yaml`, plus a co-located universal
CoreDNS egress allow) and per-service ingress/egress allow-rule templates
(`deploy/helm/telco-service/templates/networkpolicy-{ingress,egress}.yaml`), with ingress deliberately
reusing 19.3's `meshPolicy.authorizedClients` list (one source of truth for "who may call this
service," so the mesh-layer and network-layer controls cannot drift apart) and egress flags derived
from `service-catalog.md` Section 5 plus `event-catalog.md`'s Kafka roster. Reviewed and corrected one
documentation gap this session (the `keycloak: true` egress flag on all 10 domain services was
un-explained in the template's header comment; verified live via
`microservices/configs/<service>/application-docker.yml`'s `jwks-uri` that each domain service is its
own OAuth2 resource server independently validating JWTs against Keycloak, additional to the gateway's
own validation - added the missing rationale comment). Flagged, not fixed (out of this Helm-only
feature's scope): the shared-Postgres-StatefulSet architecture means 19.4.3's literal "cannot reach
another service's Postgres instance" AC can't be network-layer-enforced (isolation here is logical/
schema-level, ADR-006, not physical) - noted for a possible `tech-lead` AC re-scoping. **19.5.3** (the
one 19.5 subtask needing no cluster): repo-wide diff audit confirmed Sprint 19's entire uncommitted
changeset is confined to `deploy/`, `docs/tasks/`, and the ADR-026 status flip - zero `.java`, zero
security/config files - ADR-011's JWT/RBAC trust layer is verified unchanged. **Live verification
(kubectl/helm/linkerd against a running cluster) is NOT done this session for either 19.3, 19.4, or
19.5.1/19.5.2** - Docker Desktop was not running and `helm`/`linkerd` were not available in this
session's shell; deferred to the next cluster-available session. Nothing committed yet (matches this
sprint's existing uncommitted state from 19.1/19.2). Detail: sprint-19 README's "19.3" and "19.4"
Authoring and Static Verification Record sections, and the new "19.5.3 Verification Record" section.

Prior update, 2026-07-12 (Sprint 17 Distributed Locking - **COMPLETE, all 5/5 features DONE**. Built
this session on top of the platform foundation (17.1/17.2, see the entry directly below): Feature 17.3
(`subscription-service` MSISDN reservation-expiry reaper - `ExpireMsisdnReservationsCommand(Handler)`
drives releases through the existing `MsisdnPool.release()` domain method, one `audit_log` row per
release atomically inside the mediator transaction; `MsisdnReservationExpiryReaper` guards the tick
with an explicit-lease `DistributedLock`), Feature 17.4 (`billing-service`'s `RunBillCommandHandler`
wraps its existing bill-run orchestration in a watchdog-managed `DistributedLock` keyed on the billing
period; a new `RunBillResult.alreadyOwnedByAnotherPod()` outcome replaces an undifferentiated failure
on lock contention, with the losing side verified never to reach `subscriberRepo`/`batchProcessor` at
all), and Feature 17.5 (`docs/architecture/platform-capabilities.md`, `platform/PLATFORM-SPEC.md` -
sections 7-11 renumbered to 8-12 to insert a new platform-lock section, no repo-wide cross-reference
broken - and `platform-gap-closing-plan.md` all updated to record the capability as shipped). A first
code-review pass caught and this session fixed a real regression before it shipped: adding
`starter-lock` to both services made `DistributedLock` a MANDATORY bean dependency, but Redisson
connects eagerly at startup (unlike `starter-kafka`'s tolerant listener containers) - disabling the
lock in each service's shared test profile (the fix used to avoid needing live Redis in unrelated
tests) would otherwise have broken every pre-existing Spring-context test in both modules. Fixed by
packaging a second, inverse-conditioned `@AutoConfiguration` in `starter-lock`'s own test-jar supplying
a real in-JVM `DistributedLock` substitute whenever the real one is disabled - zero changes needed to
any pre-existing test file; a related `@Scheduled`-fires-unconditionally finding on the new reaper was
fixed the same way. Both fixes verified live (a new isolated `ApplicationContextRunner` test, 3/3
passing) and confirmed by a second review pass (APPROVE). VERIFIED LIVE this session: 3 new
Docker-independent Mockito unit test classes across both services (covering the handler/reaper lock
logic, the losing side's degrade-safely behavior, and the release/audit atomicity) all pass; full
`microservices` reactor build (subscription-service + billing-service) and full `platform` reactor
both structurally clean. NOT VERIFIED LIVE: the two new Testcontainers-based `*ConcurrencyIT` classes -
compile clean, reviewed carefully, but blocked by the same pre-existing, repo-wide Docker/Testcontainers
API-version incompatibility documented in the entry below (confirmed unrelated to this session's
changes). Nothing committed yet (user choice, consistent with the platform-foundation entry below).
Detail: sprint-17 README, `docs/tasks/lessons.md` (2026-07-12 entries).

Prior update, 2026-07-12 (Sprint 17 Distributed Locking - **started, platform foundation DONE (2/5
features)**. ADR-024 was Proposed; ratified (Accepted) by tech-lead this session with one amendment:
the architecture review found Section 5's original design - a new `LockAcquisitionException extends
PlatformException` living in the new `platform-core/lock` module - is not buildable, because
`PlatformException` (`platform-common`) is a `sealed` class whose `permits` list is closed to its own
package, and this codebase has no `module-info.java` anywhere under `platform/` (so Java's
same-package sealed-subtype rule applies, not a module-boundary one). Tech-lead's ratified fix:
`RedissonDistributedLock` throws the platform's EXISTING `DependencyFailureException` (already
503-mapped in `starter-api`'s `GlobalExceptionHandler`, unchanged) constructed with a new
`LockErrorCode.LOCK_ACQUISITION_FAILED` (an `ErrorCode` living in `platform-core/lock`, mirroring
`CommonErrorCode`) - zero changes to `starter-api`, no new exception type, no new transitive
dependency on every service. ADR-024 Sections 2 and 5 and Sprint 17 task file 17.1 were amended to
match before any code was written. Built this session: Feature 17.1 (`platform/platform-core/lock` -
`DistributedLock`, `LockHandle`, `LockErrorCode`; `platform/platform-starters/starter-lock` -
`RedissonDistributedLock`, `RedissonLockHandle`, `LockAutoConfiguration`, `LockProperties`; plain
`org.redisson:redisson`, not `redisson-spring-boot-starter`; `platform-bom` pins Redisson 3.50.0 and
both new module coordinates) and Feature 17.2 (a Testcontainers Redis harness packaged as a
`starter-lock` test-jar per the `platform-event-contracts` precedent, plus a contention/watchdog/
explicit-lease/fail-closed test suite). VERIFIED: full `platform` reactor builds clean
(`mvn -am install`, structural + spotbugs + checkstyle all pass); `platform-lock`'s dependency tree is
confirmed zero-Spring/zero-Redisson; a dedicated Spring context test proves the fail-closed path
returns HTTP 503 with `ApiError.code=LOCK_ACQUISITION_FAILED` via the UNCHANGED `GlobalExceptionHandler`
(no handler edit). NOT VERIFIED LIVE: the four Testcontainers-Redis behaviors (mutual exclusion,
watchdog liveness, explicit-lease hard-expiry, fail-closed) - this sandbox's Docker Desktop (29.1.2)
now enforces a minimum API floor of 1.44, and the repo's pinned Testcontainers 1.20.6 (matching
`microservices/pom.xml`'s existing convention, mirrored into `platform-bom` for this sprint) bundles a
`docker-java` client that negotiates API 1.32 - confirmed as a pre-existing, repo-wide environment
issue (not caused by this sprint's changes) by reproducing the identical failure on the untouched,
already-existing `starter-inbox` Testcontainers test. Deferred to a follow-up session: Features 17.3
(subscription-service MSISDN reaper), 17.4 (billing-service bill-run lock), and 17.5 (capability-catalog
docs update) - user-scoped this session to the platform foundation only. A code-review pass on 17.1/17.2
(before this DONE status was finalized) returned CHANGES REQUIRED on its first pass - a HIGH finding
(`withLock(Callable)` rewrapped domain `RuntimeException`s from a guarded action as
`IllegalStateException`, which would have broken `GlobalExceptionHandler`'s type-based dispatch for
17.3/17.4's future consumers) and two MEDIUM findings (a dead `lease-time` config property; missing
Docker-independent unit coverage for `RedissonDistributedLock`). All three were fixed (plus one LOW
Javadoc item), including a new 9-test Mockito unit suite (`RedissonDistributedLockUnitTest`, all
passing live) that directly regression-tests the HIGH finding; a second review pass returned APPROVE.
Detail: sprint-17 README, ADR-024, `docs/tasks/lessons.md` (2026-07-12 entries).

Prior update, 2026-07-12 (Sprint 18 Feature 18.5 DONE - all 5 Sprint 18 features are now
deliverable-complete and individually verified against their own subtask-level acceptance criteria
(5/5), same "features-DONE, exit-criteria-tail tracked" framing Sprint 15 used. **IMPORTANT - the
sprint's own Exit Criteria are NOT yet fully met**: "a pod for every one of the 13 services starts
successfully" is blocked platform-wide by a pre-existing, Sprint-18-unrelated config-server bug (see
below) - not by anything this sprint's Vault/CSI work introduced. Per-service DB credentials into
Vault KV v2, retiring the `docker`-profile plaintext DB block for in-cluster deployment, ADR-025
Section 2/4. **18.5.1**: extended
`deploy/helm/vault/seed-secrets.sh` to generate a real per-service DB password (`openssl rand -base64 24`)
for every PostgreSQL-backed service (`docs/architecture/service-catalog.md` Section 5 - `identity-service`,
`customer-service`, `product-catalog-service`, `order-service`, `subscription-service`, `usage-service`,
`billing-service`, `payment-service`, `notification-service` (outbox DB), `ticket-service`; 10 services),
write it to `secret/<service>/db-credentials` (keeping `username` as the existing per-service Postgres
role name from `01-create-databases.sql` - renaming the role was assessed as unnecessary DB-ownership
churn for no security benefit this feature is scoped to deliver), AND rotate the *live* Postgres role's
password to match (`ALTER USER ... WITH PASSWORD`) so the Vault value is the actually-accepted
credential, not just a Vault-side placeholder. No new Vault policy needed - confirmed the existing 18.2.2
per-service policy (`secret/data/<service>/*`) already covers the `db-credentials` path. **18.5.2**:
audited every PostgreSQL-backed service's `application-prod.yml` against `application-docker.yml` -
finding: `prod` is **not** safe to activate in-cluster as-is for **any** of the 10 services (not just
customer-service) - it externalizes DB (and, for customer/billing, MinIO) credentials but drops the
`docker` profile's Kafka `bootstrap-servers`, Keycloak JWKS URI, and (for order/usage/billing/subscription)
inter-service `telco.clients` URL overrides entirely, which would silently break Kafka, JWT validation,
and service-to-service calls if activated bare. Created `microservices/configs/<service>/application-k8s.yml`
for all 10 services - functionally `application-docker.yml` with the DB username/password replaced by
`${<SERVICE>_DB_USER}`/`${<SERVICE>_DB_PASSWORD}` placeholders (matching `application-prod.yml`'s naming
convention); jdbc URL host/port/dbname stay hardcoded (non-secret, unchanged from `application-docker.yml`).
Updated `SPRING_PROFILES_ACTIVE` in all 10 services' `deploy/helm/values/<service>.yaml` from `dev,docker`
to `dev,k8s`. **18.5.3**: no `SecretProviderClass` template change was needed (it already iterates
`.Values.vault.secretKeys` generically, 18.3.2) - added two `vault.secretKeys` entries per service
(`<SERVICE>_DB_USER`/`<SERVICE>_DB_PASSWORD`, sourced from `secret/<service>/db-credentials`'s
`username`/`password` fields) to each of the 10 `deploy/helm/values/<service>.yaml` files. **Live-verified**
on the same Kind cluster 18.4 left running: ran the extended `seed-secrets.sh` for all 10 services (not
just 2-3) - every `secret/<service>/db-credentials` write and matching Postgres `ALTER USER` succeeded.
Spot-verified `secret/order-service/db-credentials` and `secret/billing-service/db-credentials` returned
values distinct from the `order`/`order` and `billing`/`billing` committed defaults. **Important finding,
broader than 18.4's note**: building and deploying `billing-service` (new PostgreSQL-backed service,
locally built + `kind load`ed) and re-deploying `customer-service` with `vault.enabled=true` and the new
`dev,k8s` profile showed that switching the profile name does **not** sidestep the pre-existing
config-server bug 18.4 flagged for customer-service - live testing (`curl .../billing-service/dev,k8s`,
`.../customer-service/dev`, `.../identity-service/dev,k8s`, `.../ticket-service/dev,k8s`,
`.../order-service/dev,k8s`, `.../api-gateway/dev,docker` - the last one on the *original* docker profile,
confirming this is not something 18.5 introduced) all returned HTTP 500 with the same
`FailedToConstructEnvironmentException: ... found duplicate key spring` - the merge conflict is between
the root `application-dev.yml` and each service's own `application-dev.yml`, independent of the second
profile. **No PostgreSQL-backed service reaches full pod `Ready` in this cluster today**, and none did
under 18.4 either beyond config-server itself - this is not a regression introduced by 18.5, it is the
same already-flagged, out-of-scope bug now confirmed platform-wide rather than customer-service-specific.
Not fixed here (Java/config-server-adjacent, explicitly out of `deploy/` scope per this feature's own task
spec). Because the app never reaches `DataSource` creation when config fetch 500s, full-`Ready`-implies-DB-
connectivity could not be used as the verification method; instead, DB credential delivery was proven
directly and rigorously: `kubectl exec deploy/billing-service -- env` and `kubectl exec deploy/customer-service
-- env` (customer-service was re-upgraded to `vault.enabled=true`/`dev,k8s` for this) showed
`BILLING_DB_USER=billing`/`BILLING_DB_PASSWORD=<fresh Vault value>` and
`CUSTOMER_DB_USER=customer`/`CUSTOMER_DB_PASSWORD=<fresh Vault value>` respectively, both byte-for-byte
matching `vault kv get secret/<service>/db-credentials` - confirming the CSI sync delivers the new keys
correctly. Then, from `postgres-0`, connected to Postgres over its **Service IP** (not `localhost`, which
hits a `trust`-auth loopback rule in this cluster's `pg_hba.conf` and would prove nothing) using each
service's exact injected password: `psql -h <postgres-0 IP> -U billing -d billing_db` succeeded with the
Vault value and **failed with `password authentication failed`** using the old `billing`/`billing` default;
identical result for `customer`/`customer`. This proves the rotated credential is genuinely required for
DB access, end to end, independent of the blocked app-level verification path. **Not attempted**: DB
credential seeding/rotation for `product-catalog-service`, `subscription-service`, `usage-service`,
`payment-service`, `notification-service`; `seed-secrets.sh` ran for all 10 and Vault holds a value for
each (verified for `order-service`/`billing-service`), and the live psql-level proof was only additionally
done for `billing-service` and `customer-service` (2 of 10) - do not read this as "all 10 services proven
DB-connectivity-live", only "all 10 have real Vault+Postgres-rotated credentials; 2 of 10 individually
proved to authenticate over the network with them". Confirmed no touched service's `SPRING_PROFILES_ACTIVE`
retains `docker` (`dev,k8s` verified live for `billing-service` and `customer-service`; verified in the
committed values files for the other 8). Kind cluster torn down after this session - Sprint 18 is complete,
no further feature needs it kept alive.)

Last updated: 2026-07-12 (Sprint 18 Feature 18.4 DONE - migrated `ENCRYPT_KEY`, `CUSTOMER_AES_KEY`,
`CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD` from committed Helm dev defaults into
Vault KV v2, ADR-025 Section 2. Added `deploy/helm/vault/seed-secrets.sh` (18.4.1): generates
`ENCRYPT_KEY` via `openssl rand -hex 32`, `CUSTOMER_AES_KEY` via `openssl rand -base64 32` (satisfies
`AesKeyProvider`'s 32-byte AES-256 decode check), and one shared value each for
`CONFIG_SERVER_PASSWORD`/`EUREKA_PASSWORD`/`REDIS_PASSWORD` (per ADR-025 Section 2, these are shared
credentials - same value, written to every consuming service's own `secret/<service>/app` path so Vault
policy still scopes *who* can read it) and writes them via `vault kv put` at the exact paths the 13
services' `vault.secretKeys` (Feature 18.3) already expect - `secret/config-server/encrypt-key`,
`secret/customer-service/aes-key`, `secret/<service>/app` per service. Retired the committed DEV-ONLY
values in `deploy/helm/values/config-server.yaml` and `customer-service.yaml` (18.4.2): `ENCRYPT_KEY`
and `CUSTOMER_AES_KEY` are now obvious, non-random repeating-pattern placeholders (still valid
64-hex-char / 32-byte-base64 so local `vault.enabled=false` boots) that can never be mistaken for real
key material; rewrote `deploy/helm/README.md` "Config / Secret model" and `deploy/RUNBOOK.md` Section 4
(+ new Section 14) to present `vault.enabled=true` as the primary path for any non-local environment and
the static `secrets:` map as explicitly local-dev-only, with a coordination note for the `security` agent
to close `docs/architecture/security-posture.md` Section 10's "Real secrets from Vault/K8s Secret" item
(not edited directly, out of this feature's scope). **Bug found and fixed** (surfaced only under real
Vault values, in-scope per this feature's own bug-fix allowance): `deploy/helm/values/config-server.yaml`
hardcoded a dev Basic-Auth `Authorization` probe header (`base64("config:config")`); `/actuator/health` is
`permitAll` in `ConfigServerSecurityConfig` so no header was ever required, but Spring Security's
`BasicAuthenticationFilter` 401s on an *invalid* credential before authorization runs regardless of
`permitAll` - this silently prevented config-server from ever reaching `Ready` once `CONFIG_SERVER_PASSWORD`
became a real (non-`"config"`) Vault value. Fixed by removing the header entirely (not needed in either
mode). **Bug found and fixed in `seed-secrets.sh` itself** (Git-Bash-on-Windows-specific): `openssl rand
-base64 24 | tr -d '=+/\n'` left a trailing `\r` on every generated password (CRLF line ending), silently
corrupting Basic Auth even though `kubectl exec ... -- env` output looked identical for both client and
server; fixed by also stripping `\r`. Live-verified on a fresh Kind cluster (created for this
verification, left running for 18.5): Vault (18.1) initialized/unsealed, `bootstrap-k8s-auth.sh` (18.2)
re-run, `csi-driver` (18.3) installed, `telco-deps` (postgres/redis needed for this verification) and
`seed-secrets.sh` (18.4.1) run - `vault kv get secret/config-server/encrypt-key` returned a 64-hex-char
value distinct from the retired dev default, `secret/customer-service/aes-key` decoded to exactly 32
bytes. Installed `config-server` and `customer-service` with `--set vault.enabled=true` using locally
built images (`telco-config-server:local`, `telco-customer-service:local`, retagged/`kind load`ed):
**config-server reached `1/1 Ready`**, and `kubectl exec deploy/config-server -- env` showed the real
Vault-generated `ENCRYPT_KEY`/`CONFIG_SERVER_PASSWORD`/`EUREKA_PASSWORD` (not the retired dev defaults) -
meeting the acceptance criterion in full. **customer-service reached `Running` with the real Vault-sourced
env values confirmed** (`CUSTOMER_AES_KEY`, `CONFIG_SERVER_PASSWORD`, `EUREKA_PASSWORD`, `REDIS_PASSWORD`
all distinct from dev defaults, matching what was seeded) and its Basic-Auth to config-server was proven
to work with the real Vault-sourced credential (the request moved from `401 Unauthorized` before the
`\r`-stripping fix to authenticated `200`/`500` after it) - but the pod did **not** reach full `1/1 Ready`
because of a separate, pre-existing, Vault-unrelated bug newly discovered during this verification: 11 of
13 services' `microservices/configs/<service>/application-dev.yml` each declare their own top-level
`spring:` key, and when config-server's native repository merges that with the shared
`microservices/configs/application-dev.yml` (which also declares `spring:`) for a multi-profile request
(`dev,docker`), it throws `FailedToConstructEnvironmentException: ... found duplicate key spring`. This
was never hit before because no domain service had previously gotten past config-server's Basic-Auth step
in a live cluster test (Sprint 15's tail: only discovery-server/config-server/api-gateway/product-catalog-service
were ever fully live-verified, and api-gateway's own `application-dev.yml` was never exercised together with
the root file's `dev,docker` combination in-cluster either) - it is unrelated to Vault/secrets and requires
editing `microservices/configs/*.yml` content (domain-engineer/event-integration territory, not `deploy/`),
so it was **not** fixed here and is flagged as a new follow-up. **Policy-deletion negative test passed
live**: `vault policy delete customer-service` followed by a pod recreate produced a `FailedMount` event -
`error making mount request: ... GET .../secret/data/customer-service/app Code: 403 ... permission
denied` - proving per-service Vault policy scoping is enforced, not just documented; policy restored
afterward and a subsequent pod recreate mounted successfully again. **Honest scope note**: per this
feature's acceptance criteria, the stated minimum bar - config-server AND customer-service pods carrying
real Vault-sourced secret env values, plus the policy-deletion negative test - is met in full (config-server
additionally reached full `Ready`; customer-service's secret delivery chain is proven end-to-end even
though its own `Ready` state is blocked by the unrelated config-content bug above). The sprint README's
broader "all 13 services boot" framing was NOT attempted for the remaining 11 services (consistent with
Sprint 15's own unresolved tail, out of this feature's scope per its own task spec) and remains aspirational;
do not read 18.4 DONE as a full 13-service live boot. Kind cluster left running (18.5 - per-service DB
credentials into Vault - is still TODO and needs it).)

Last updated: 2026-07-12 (Sprint 18 Feature 18.3 DONE - Secrets Store CSI Driver + Vault CSI provider,
`SecretProviderClass` per service, `secretObjects` sync to `<service>-secret`, ADR-025 Section 1. Added
`deploy/helm/csi-driver/` wrapping the upstream `secrets-store-csi-driver` chart (kubernetes-sigs) as a
vendored dependency (same `Chart.yaml`/`helm dependency update`/`Chart.lock` pattern as `deploy/helm/vault/`),
with `syncSecret.enabled: true` (required for the `secretObjects` sync, otherwise the driver only mounts
files). The Vault CSI provider is NOT a separately published chart - HashiCorp ships it as the
`csi.enabled` sub-block of the `hashicorp/vault` chart itself - so it is enabled via
`deploy/helm/vault/values.yaml` (`vault.csi.enabled: true`) rather than as a second chart; both together
satisfy the "CSI driver + Vault CSI provider, both DaemonSets" requirement. Added
`deploy/helm/telco-service/templates/secretproviderclass.yaml` (new template, rendered only when
`vault.enabled: true`), gated `templates/secret.yaml` to render only when `vault.enabled: false` (default,
local/dev unchanged), and added a CSI volume + read-only volumeMount to `templates/deployment.yaml`, gated
the same way - `envFrom.secretRef` itself was not touched. Added a `vault:` block to
`deploy/helm/telco-service/values.yaml` (`enabled: false` default, `address`, `role`, `secretKeys: []`) and
a `vault.secretKeys` override to all 13 `deploy/helm/values/<service>.yaml` files, mapping each service's
actual secret keys (per `deploy/helm/README.md`'s secret-key table) to Vault KV v2 paths: a shared
`secret/<service>/app` path for `CONFIG_SERVER_PASSWORD`/`EUREKA_PASSWORD`/`REDIS_PASSWORD`, and dedicated
paths for `config-server`'s `ENCRYPT_KEY` (`secret/config-server/encrypt-key`) and `customer-service`'s
`CUSTOMER_AES_KEY` (`secret/customer-service/aes-key`), matching ADR-025 Section 2's named paths. Updated
`deploy/helm/README.md` (chart-layout tree, install order steps 3/4/5, "Config / Secret model" rewritten to
document both modes, "Validate" section covers both `vault.enabled` values) and `deploy/RUNBOOK.md`
(Section 2.5 CSI driver DaemonSet readiness wait + `kubectl get csidriver`, Section 4 rewritten). No Helm
binary/CLI tools were preinstalled in this session's environment - `helm` v3.19.0 and `kind` v0.30.0 were
downloaded directly (network egress available) into the session scratchpad to perform validation.
`helm lint`/`helm template` green for all 13 services in both `vault.enabled=false` and `vault.enabled=true`,
and for the new `csi-driver` chart; confirmed the `vault.enabled=true` render of `customer-service` has no
static `kind: Secret` and instead a `SecretProviderClass` + CSI volume, with the `envFrom.secretRef` block
byte-for-byte identical to the `vault.enabled=false` render. Live-verified end to end on a fresh Kind
cluster (created for this verification, torn down afterward - none left running, since 18.4 will need a
fresh Vault init/unseal cycle regardless): installed `vault` (18.1, with `csi.enabled: true`) + init/unseal
+ `bootstrap-k8s-auth.sh` (18.2, re-verified live in the process) + `csi-driver` (18.3.1) - both
`csi-driver-secrets-store-csi-driver` and `vault-csi-provider` DaemonSets reached `1/1 Ready` on the
single-node cluster and `kubectl get csidriver secrets-store.csi.k8s.io` returned the object. Wrote real
test values into `secret/customer-service/app` (`CONFIG_SERVER_PASSWORD`/`EUREKA_PASSWORD`/`REDIS_PASSWORD`)
and `secret/customer-service/aes-key` (`CUSTOMER_AES_KEY`) via `vault kv put` (placeholder test values -
real migration is Feature 18.4). Applied the real chart-rendered `SecretProviderClass` + `ServiceAccount`
for `customer-service` (`helm template ... --show-only`, not hand-authored) and a test pod mounting it
under that `ServiceAccount`; the pod reached `Running`, `kubectl get secret customer-service-secret`
existed with exactly the 4 expected keys, and decoding each one
(`kubectl get secret ... -o jsonpath ... | base64 -d`) matched the test values written to Vault verbatim
(`CONFIG_SERVER_PASSWORD=test-config-pw-18.3`, `EUREKA_PASSWORD=test-eureka-pw-18.3`,
`REDIS_PASSWORD=test-redis-pw-18.3`, `CUSTOMER_AES_KEY=dGVzdC1jdXN0b21lci1hZXMta2V5LTE4LjM=`); the CSI
volume's mounted files (`/mnt/secrets-store/CONFIG_SERVER_PASSWORD`, etc.) matched too. Feature 18.4/18.5
remain TODO; nothing in the 13 services' application code, Dockerfiles, or `envFrom` changed.)

Prior update, 2026-07-12 (Sprint 18 Feature 18.2 DONE - Vault Kubernetes auth method + per-service KV v2
policies/roles, ADR-025 Section 1/2. Added a `ClusterRoleBinding` granting Vault's own `ServiceAccount`
`system:auth-delegator` (`deploy/helm/vault/templates/auth-delegator-clusterrolebinding.yaml`, installed
automatically with the `vault` release); one least-privilege KV v2 policy per service, scoped to exactly
`secret/data/<service>/*` + `secret/metadata/<service>/*` read/list
(`deploy/helm/vault/policies/<service>.hcl`, all 13 services - every service reads at least
`EUREKA_PASSWORD` per `deploy/helm/README.md`'s "Config / Secret model" table); one
`auth/kubernetes/role/<service>` per service binding `bound_service_account_names=<service>`,
`bound_service_account_namespaces=telco`, `policies=<service>`, `ttl=15m`, matching the ServiceAccount
name the `telco-service` chart's `serviceaccount.yaml` already provisions (no service overrides
`serviceAccount.name`). All of the above is scripted, idempotent, and re-runnable via
`deploy/helm/vault/bootstrap-k8s-auth.sh`. `deploy/RUNBOOK.md` gained a new Section 13 ("Vault Kubernetes
auth method and per-service policies") - appended at the end rather than inserted after Section 3, to
avoid invalidating the numbered cross-references several already-drafted Sprint 19/20 task specs make to
this document's current Sections 4/6/8/9/12. Live-verified end to end on a fresh Kind cluster (created for
this verification, torn down afterward - none left running): `vault auth list` showed `kubernetes/`
enabled, `vault read auth/kubernetes/config` returned `kubernetes_host: https://10.96.0.1:443` (the
in-cluster API service IP), `vault policy read customer-service`/`billing-service` returned the correctly
scoped policies, `vault read auth/kubernetes/role/customer-service` showed the expected bindings. Real
`ServiceAccount`s `customer-service` and `billing-service` were created via
`helm template ... -s templates/serviceaccount.yaml` (the actual chart template, not hand-authored) and
bound to lightweight debug pods (full JVM service images were not needed for this auth-only feature - no
application code path is exercised). A real `vault write auth/kubernetes/login role=customer-service
jwt=<projected-token>` from inside the live `customer-service`-ServiceAccount pod succeeded and returned a
token scoped to `["customer-service","default"]` only; that token read `secret/data/customer-service/*`
(200) and was denied `secret/data/billing-service/*` (403 permission denied) - and symmetrically, a
`billing-service`-scoped login token read its own path (200) and was denied `customer-service`'s path
(403), confirming Vault-enforced per-service isolation in both directions. Features 18.3-18.5 remain TODO;
nothing in the 13 services' application code, Dockerfiles, or `envFrom` changed.)

Prior update, 2026-07-12 (Sprint 18 Feature 18.1 DONE - Vault Helm release, standalone/Raft, unseal
procedure. Added `deploy/helm/vault/` wrapping the official `hashicorp/vault` chart as a dependency
(Chart.yaml `dependencies:` entry, `helm dependency update`-vendored `charts/vault-0.34.0.tgz` +
`Chart.lock`, mirroring the repo's existing chart-vendoring convention), pinned standalone mode
(`server.standalone.enabled: true`, `server.ha.enabled: false`, replicas 1) with Integrated Storage
(Raft) as the storage backend and the Vault Agent sidecar injector disabled (ADR-025 Section 1).
`deploy/helm/README.md` chart-layout tree, install order, and "HPA / PDB" singleton section updated;
`deploy/RUNBOOK.md` gained a new Section 3 ("Vault initialization and unseal", Shamir/manual, no key
material committed) and a Vault readiness wait in Section 2 - all later sections renumbered by one.
Live-verified end to end on a fresh Kind cluster (none was running at session start, despite the prior
entry below claiming one was left up - created and later torn down for this verification, no
pre-existing cluster state touched): `helm lint`/`helm template` green, `helm install vault` succeeded,
`vault operator init` + 3-of-5 `vault operator unseal` brought Vault to `Initialized: true, Sealed:
false`, pod reached `1/1 Ready`, and `kubectl get pdb`/`get hpa`/`get deployment` in the `telco`
namespace confirmed no PDB, no HPA, and no injector Deployment target the Vault StatefulSet. One
correction made during live verification: the task's suggested `kubectl rollout status
statefulset/vault` does not work (the upstream chart uses `updateStrategyType: OnDelete`, which
`rollout status` refuses to track) and `wait --for=condition=ready` would hang until after unseal
(Vault's readiness probe runs `vault status`, which fails while sealed) - replaced with `kubectl wait
--for=condition=Initialized` in both `deploy/RUNBOOK.md` and `deploy/helm/README.md`. Features 18.2-18.5
remain TODO; nothing in the 13 services' application code, Dockerfiles, or `envFrom` changed.)

Prior update, 2026-07-12 (Sprint 15 exit-criteria follow-ups - **two of three RESOLVED live on Kind;
one remains**. Reopened the two tracked deployment blockers on the live Kind cluster and closed both
with evidence. (1) schema-registry in-cluster crash-loop: the originally-recorded root cause
(KafkaStore-init timeout, "add a wait-for-kafka init-container") was DISPROVEN by the actual crashed-pod
evidence and the pre-approved fix was correctly NOT applied. Real, live-confirmed root cause: a
Kubernetes service-link env collision - the Service named `schema-registry` makes kubelet inject
`SCHEMA_REGISTRY_PORT=tcp://<ip>:8081`, which cp-schema-registry's entrypoint reads as the deprecated
PORT setting and hard-exits 1 in the configure stage before Kafka is ever contacted (zero log4j output
was the tell). Fix: `enableServiceLinks: false` on the schema-registry Deployment pod spec
(`deploy/helm/dependencies/templates/schema-registry.yaml`); verified live Running 1/1, 0 restarts,
`/subjects` serving. (2) product-catalog 500 on GET /api/v1/tariffs in-cluster: ENVIRONMENTAL, not a
code defect - the list endpoint is uncached and the earlier 500 occurred only during a
thrashing/partial-wave cluster state; returned HTTP 200 with the correct ApiResult shape once the
dependency layer was healthy. No code change. Incidental live finding: kafka-0's exit-143 churn was a
liveness-probe kill under single-node CPU pressure (HPAs had inflated app replicas 5x), cleared by
pinning the api-gateway + product-catalog HPAs to 1/1 - no chart change. The dependency layer + config/
discovery/gateway/product-catalog are all Running 1/1. STILL REMAINING (the one item between
"feature-complete + deployable" and "AC proven green in Kubernetes"): the full 13-service in-cluster
boot - the other 9 domain services are not yet imaged/deployed on the local node and the 10 Debezium
outbox connectors are not registered - then the deployed-environment AC-01/02/03 run. Detail:
`docs/tasks/todo.md` ("Closing the Sprint 15 exit-criteria tail"), `docs/tasks/lessons.md` (2026-07-12
entry), and `docs/tasks/sprint-15-deployment/README.md` (Exit-Criteria Follow-Ups). Nothing committed
yet (user choice); the Kind cluster is left running.

Prior update, 2026-07-11 (Roadmap-extension documentation pass - **planning and design only; nothing
built, nothing IN PROGRESS, nothing DONE**. Sprint 16 (Web Frontend, post-MVP) was detailed: its 5
feature task files (16.1-16.5) were authored, replacing the prior "to be authored when the sprint is
scheduled" placeholder; Sprint 16 stays **TODO 0/5**. Seven brand-new post-MVP sprints were scaffolded
end to end - each with a sprint README and Features table, and (except Sprint 20) a new Proposed-status
ADR - so this single effort now covers 8 requested capabilities in total: BFF/web frontend (Sprint 16,
above, ADR-022), Sprint 17 Distributed Locking (new `starter-lock` platform module, Redisson-backed,
ADR-024 Proposed), Sprint 18 Secret Management (HashiCorp Vault, Kubernetes auth method + Secrets Store
CSI driver, ADR-025 Proposed, extends/replaces Sprint 15's K8s-Secret-only model), Sprint 19 Service
Mesh and mTLS (Linkerd + default-deny NetworkPolicies, ADR-026 Proposed, closes the mTLS deferral
recorded in `docs/architecture/security-posture.md` Section 8, sequenced after Sprint 18 for
operational reasons only - no hard technical dependency), Sprint 20 Chaos Engineering (Chaos Mesh fault
injection - pod-kill/latency/network-partition - plus a game-day runbook on the existing Kind/Helm
baseline; explicitly extends ADR-012/ADR-013 per a tech-lead ruling, no new ADR), Sprint 21
Campaign/Catalog Validation (new `campaign-service`, CQRS+Mediator, port 9011 proposed, ADR-027
Proposed - the buildable subset of the campaign/promotion engine already tracked in
`docs/product/roadmap.md` Section 5 and `docs/product/TELCO-CRM-ADVANCED.md` Section 2.4), Sprint 22
Invoice Dispute/Chargeback (new `dispute-service`, Domain Orchestration, port 9012 proposed, ADR-028
Proposed - genuinely new scope, not previously listed in the roadmap or in ADVANCED.md), and Sprint 23
SIM-Swap/Fraud Detection (new `fraud-service`, CQRS+Mediator, port 9013 proposed, ADR-029 Proposed - a
deliberately narrowed, rule-based MVP subset of the streaming/ML fraud-service in ADVANCED.md
Section 4.4). All 7 new sprints (17-23) are **TODO** with 0 features started; see the Sprint Rollup
table below for exact per-sprint feature counts. No code was written, no service was scaffolded, and no
ADR was ratified this session - every new ADR (022 already Accepted from a prior session; 024-029) is
either already-Accepted (022) or Proposed pending tech-lead ratification before its sprint's build work
starts. This entry, together with the matching `docs/product/roadmap.md` update (new Phase P6 -
Post-MVP Depth, and a restructured Section 5 Post-MVP Candidates), is the reconciliation step that makes
both documents the accurate, single source of truth for this newly documented (not yet built) scope.
Detail: each sprint's own README under `docs/tasks/sprint-16-web-frontend/`,
`docs/tasks/sprint-17-distributed-locking/`, `docs/tasks/sprint-18-secret-management/`,
`docs/tasks/sprint-19-service-mesh-mtls/`, `docs/tasks/sprint-20-chaos-engineering/`,
`docs/tasks/sprint-21-campaign-catalog-validation/`, `docs/tasks/sprint-22-dispute-chargeback/`,
`docs/tasks/sprint-23-sim-swap-fraud/`, and the corresponding ADRs under `architecture/adr/`
(ADR-022, ADR-024 through ADR-029).

Prior update, 2026-07-08 (Sprint 15, Feature 15.5 Release Documentation **DONE** - all 5 Sprint 15
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
| [17](sprint-17-distributed-locking/README.md) | distributed locking, `starter-lock` (Redisson) (**post-MVP**) | DONE | 5/5 |
| [18](sprint-18-secret-management/README.md) | secret management, HashiCorp Vault (**post-MVP**) | DONE (features); exit follow-ups tracked | 5/5 |
| [19](sprint-19-service-mesh-mtls/README.md) | service mesh and mTLS, Linkerd (**post-MVP**) | IN PROGRESS | 2/5 (19.3+19.4 authored/statically verified; 19.5.3 done; 19.5.1/19.5.2 + both features' live-verify blocked on cluster) |
| [20](sprint-20-chaos-engineering/README.md) | chaos engineering, Chaos Mesh (**post-MVP**) | IN PROGRESS | 5/5 authored, 0/5 live-verified |
| [21](sprint-21-campaign-catalog-validation/README.md) | campaign-service, dynamic pricing/catalog validation (**post-MVP**) | TODO | 0/5 |
| [22](sprint-22-dispute-chargeback/README.md) | dispute-service, invoice dispute/chargeback (**post-MVP**) | DONE (code-complete) | 6/6 |
| [23](sprint-23-sim-swap-fraud/README.md) | fraud-service, SIM-swap/fraud detection (**post-MVP**) | TODO | 0/5 |

Totals (MVP, Sprints 01-15): all 15 sprints feature-complete. Features: 77 DONE / 0 IN PROGRESS
/ 0 TODO / 0 BLOCKED (77 total). Sprint 15 (Deployment) closed all 5 features on 2026-07-08 -
deliverables built and each individually verified (much of it live on a Kind cluster) - BUT its
platform-level exit criteria ("all MVP AC hold in the DEPLOYED environment") are not yet fully met:
a fully-green 13-service in-cluster boot + the deployed-environment acceptance run remain. Of the two
tracked deployment blockers, BOTH were RESOLVED live on 2026-07-12 (schema-registry exit-1 -> a K8s
service-link env collision, fixed with `enableServiceLinks: false`; product-catalog in-cluster 500 ->
environmental, returns 200 on a healthy dependency layer, no code change). The one item still standing
is the always-deferred full 13-service boot itself: the other 9 domain services are not yet imaged/
deployed on the local node and the 10 Debezium outbox connectors are not registered, after which the
deployed-environment AC-01/02/03 run can execute. So the MVP is feature-complete and deployable, with a
short, well-scoped integration tail (the full boot) before "runs green end-to-end in Kubernetes" is
literally true. See the top-of-file entry, `docs/tasks/todo.md`, and `deploy/RUNBOOK.md` Section 11.
Sprints 16-23 are post-MVP (Sprint 16: ADR-022, Accepted; Sprint 17: ADR-024, Accepted 2026-07-12,
**DONE 5/5**; Sprint 18: ADR-025, Accepted, **DONE (features) 5/5, exit-criteria tail tracked** (a
pre-existing, Sprint-18-unrelated config-server multi-profile bug blocks the sprint's own "every pod
starts" exit criterion - see the 2026-07-12 entries above); Sprints 19 and 21-23: ADR-026 through
ADR-029, all Proposed pending tech-lead ratification; Sprint 20: extends ADR-012/ADR-013, no new ADR)
and excluded from the MVP totals. Sprints 17 and 18 are complete (18 with a tracked follow-up); the
other 6 remain documentation/design only as of 2026-07-11 - TODO, not started. See Phase P6 below and
[`docs/product/roadmap.md`](../product/roadmap.md) Section 3.
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
| EPIC-016 Web Channel | P6 | Web frontend + web-bff (ADR-022) | 16 |
| EPIC-017 Distributed Coordination | P6 | Redis-backed distributed locking, `starter-lock` (ADR-024 Accepted) | 17 |
| EPIC-018 Secret Management | P6 | Vault-backed secrets, K8s auth method + CSI-synced secrets (ADR-025 Accepted) | 18 |
| EPIC-019 Zero-Trust Networking | P6 | Service mesh mTLS + default-deny NetworkPolicies (ADR-026 Proposed) | 19 |
| EPIC-020 Chaos Engineering | P6 | Fault injection + game days, extends ADR-012/ADR-013 (no new ADR) | 20 |
| EPIC-021 Campaign and Catalog Validation | P6 | `campaign-service`, dynamic pricing/redemption limits (ADR-027 Proposed) | 21 |
| EPIC-022 Invoice Dispute and Chargeback | P6 | `dispute-service`, invoice dispute/PSP chargeback orchestration (ADR-028 Proposed) | 22 |
| EPIC-023 SIM-Swap and Fraud Detection | P6 | `fraud-service`, rule-based fraud detection, MVP scope (ADR-029 Proposed) | 23 |

Phase P6 ("Post-MVP Depth") is the immediate post-MVP delivery increment covering Sprints 16-23; all
of EPIC-016 through EPIC-023 are TODO as of 2026-07-11 - documented and design-reviewed, not built. See
[`docs/product/roadmap.md`](../product/roadmap.md) Section 3 ("P6 - Post-MVP Depth") for the phase
detail and its explicit disambiguation from `docs/product/TELCO-CRM-ADVANCED.md`'s own P6-P11
forward-looking phase lettering (Section 10 of that document), which this phase is distinct from.

## How to Update Status

1. Change the feature row in the owning sprint `README.md` Features table.
2. Recompute that sprint's header `Progress` (DONE features / total) and `Status`.
3. Update the matching row in the Sprint Rollup table above and the `Last updated` dates.
4. If the change closes out an epic, update the Epics and Phases table above.
