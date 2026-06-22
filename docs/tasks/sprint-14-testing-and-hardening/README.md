# Sprint 14 - Testing and Hardening

## Objective

Raise the platform to production quality: full acceptance-criteria validation, contract testing of
event schemas and APIs, security hardening (PII-at-rest encryption coverage, audit-log completeness,
PII telemetry masking, mTLS posture decision), and performance validation against the NFR targets.

Covers NFR-01, NFR-02, NFR-06, NFR-12, NFR-16, NFR-17 and final validation of AC-01/02/03.

## Included Epics

- Epic 14: Quality, Security, and Performance Hardening

## Features (one file per top-level task)

| ID | Feature | File |
| --- | --- | --- |
| 14.1 | Acceptance and End-to-End Testing | [14.1-acceptance-and-end-to-end-testing.md](14.1-acceptance-and-end-to-end-testing.md) |
| 14.2 | Security Hardening | [14.2-security-hardening.md](14.2-security-hardening.md) |
| 14.3 | Performance Validation | [14.3-performance-validation.md](14.3-performance-validation.md) |

## Sprint Deliverables

- Automated acceptance suite (AC-01/02/03 incl. compensation), event/API contract tests, and a
  coverage gate in CI.
- Security hardening: verified PII encryption at rest, PII telemetry masking, audit-log completeness,
  and a documented mTLS/security posture.
- Performance validation against NFR-01 (p95 latency) and NFR-02 (bill-run throughput).

## Exit Criteria

- All MVP acceptance criteria pass end to end in CI; contract tests guard event/API boundaries.
- PII is encrypted at rest and masked in telemetry everywhere; audit logging is complete in the four
  mandated services; no high-severity security findings remain.
- NFR-01 and NFR-02 targets are met and recorded.
</content>
