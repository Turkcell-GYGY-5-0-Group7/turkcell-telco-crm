# Working TODO — Sprint 14: Testing and Hardening

Sprint 13 verified (platform builds; 5/5 resilience tests pass; observability artifacts present).
Now executing Sprint 14. Mode: plan -> approve -> execute, review at feature boundaries.

Objective: raise the platform to production quality — acceptance validation, contract tests,
security hardening, performance validation. Covers NFR-01, NFR-02, NFR-06, NFR-12, NFR-16, NFR-17
and final validation of AC-01/02/03.

## Proposed execution order (code/test-level first, stack-heavy last)

### Wave A — no live stack needed (Testcontainers / code / CI)
- [x] 14.1.2 Contract tests for events + APIs (M) — event-integration  [DONE, verified]
      Generalized avsc-snapshot+reflection guards for all produced events (tariff/order/payment/
      usage-aggregated/invoice/ticket/notification) + capture-based guards for Map.of events + provider
      API contract tests (tariff/customer/order). Guard proven to fail on breaking change. BUILD SUCCESS.
- [x] 14.1.3 Coverage gate (S) — devops  [DONE, verified]
      JaCoCo check goal, 70% line/module, warn-first (jacoco.haltOnFailure=false) in microservices/pom.xml;
      CI wired in ci.yml. Flip = set haltOnFailure=true. Observed: customer 86.4%, notification 86.6%.
- [x] 14.2.1 PII-at-rest encryption audit (M) — security  [PASS]
      TCKN AES-256-GCM, keys from secrets, ciphertext proven by test; no card data stored (PSP-delegated).
- [x] 14.2.2 PII telemetry masking audit (M) — security  [PASS + 2 minor recs]
      All 4 PII types covered. Rec: SimCard.msisdn lacks @Sensitive (backstop-masked only); JSON path
      relies on Layer A @Sensitive — keep review rule against raw-PII log interpolation.
- [x] 14.2.3 Audit-log completeness (M) — security  [GAPS FIXED, verified]
      identity/subscription PASS. FIXES DONE:
        * payment-service HIGH: V3__audit_log.sql + domain/AuditLog + AuditLogRepository + AuditLogWriter;
          wired into ChargePaymentCommandHandler (PAYMENT_COMPLETED/PAYMENT_FAILED) +
          RefundPaymentCommandHandler (PAYMENT_REFUNDED); tests verify audit calls. Payment 8/8 pass.
        * customer MEDIUM: AuditLogWriter injected into AddAddress/UpdateAddress/SetDefaultAddress
          (ADDRESS_ADDED/UPDATED/SET_DEFAULT); tests verify. Address tests 10/10 pass.
      Both modules BUILD SUCCESS. (Full DB-row proof deferred to Wave B integration/E2E.)
- [x] 14.2.4 mTLS posture + security review (M) — security  [PASS]
      security-posture.md written; no stack-trace leakage (GlobalExceptionHandler generic 500);
      rate limit + gateway-behind-trust confirmed.

Audit report: docs/tasks/sprint-14-testing-and-hardening/14.2-security-audit-report.md
Posture doc:  docs/architecture/security-posture.md

### Wave B — needs full running stack (Docker + all services + gateway + observability)
Core infra UP + healthy (postgres, redis, kafka, kafka-connect/Debezium, schema-registry, minio).
14.1.1 approach (confirmed): author full compose-based gateway-driven RestAssured suite (AC-01/02/03 +
AC-01 compensation); add the 10 domain services to a compose 'apps' profile; wire a CI job; prove AC-01
end-to-end locally, full matrix in CI.
  Steps: (1) read compose.yml + a service Dockerfile/application-docker.yml + gateway routes + keycloak
  realm + AC-01 saga/welcome-SMS assertion point. (2) add 'apps' profile (10 services, SPRING_PROFILES_
  ACTIVE=dev,docker, depends_on, healthchecks). (3) new acceptance-tests Maven module (RestAssured via
  gateway). (4) CI job brings stack up + runs suite. (5) local AC-01 slice run + observe.

- [ ] 14.1.1 Full acceptance suite AC-01/02/03 + AC-01 compensation (L) — qa
      Automated end-to-end through the gateway; runs in CI.
      AC: AC-01/02/03 pass end-to-end incl. compensation.
- [ ] 14.3.1 API latency load test NFR-01 (M) — devops + qa
      k6/Gatling on representative read/write endpoints through gateway; p95 < 300ms.
      AC: p95 < 300ms at target load; recorded in dashboard.
- [ ] 14.3.2 Bill-run throughput NFR-02 (L) — qa + domain-engineer
      Seed 100K active postpaid subscribers; measure full bill-run < 30 min, no duplicates.
      AC: 100K bill-run < 30 min with no duplicate invoices.  [DECISION: scale on this machine]

## Decisions (confirmed 2026-07-03)
1. Coverage gate: 70% line per module, WARN-FIRST (report/non-blocking), flip to hard-fail later.
2. Load-test tool: k6.
3. Bill-run scale: reduced-N (~10K) locally to prove correctness + no duplicates; true 100K in CI;
   document the extrapolation method.
4. Wave B needs Docker Desktop + full compose stack (user starts Docker manually).

## Review checkpoints
- After Wave A: report audit findings + any hardening fixes before touching Wave B.
- After each Wave B feature: report result before proceeding.
- Update owning sprint README + STATUS.md together as each feature reaches DONE. Capture lessons.
