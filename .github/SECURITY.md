# Security Policy

The Telco CRM Platform handles regulated, privacy-sensitive data (KVKK/GDPR). Security is a
first-class requirement. This policy describes how to report vulnerabilities and the security
expectations for contributors. The authoritative technical security design is ADR-011.

## Supported Versions

The MVP is under active development. Security fixes target the `master` branch. Until a formal
release scheme exists, only the latest `master` is supported.

| Version | Supported |
| --- | --- |
| master (latest) | Yes |
| pre-release branches | No |

## Reporting a Vulnerability

Please report security vulnerabilities privately. Do not open a public issue.

- Preferred: open a private advisory via GitHub Security Advisories
  (https://github.com/omerekmen/turkcell-gygy-java/security/advisories/new).
- Alternative: email subscription@omerekmen.com with the details.

Please include:

- A description of the vulnerability and its impact.
- Steps to reproduce or a proof of concept.
- Affected service(s) and version/commit.
- Any suggested remediation.

Do not include real customer PII in your report. Use redacted or synthetic data.

## Response Expectations

- Acknowledgement: within 3 business days.
- Initial assessment: within 10 business days.
- We will keep you informed of progress and coordinate a disclosure timeline.

## Security Expectations for Contributors

These reflect ADR-011 and the platform rules:

- Authentication and authorization are enforced at the API Gateway (OAuth2/JWT). Services
  trust gateway-injected identity headers; internal traffic uses JWT plus mTLS.
- PII (TCKN, card numbers) is encrypted at rest with AES-GCM; keys come from Vault/Kubernetes
  Secrets. Never log PII in plaintext.
- Audit logging is mandatory in identity, customer, payment, and subscription services.
- Rate limiting is enforced at the gateway (Redis-backed).
- Never commit secrets, tokens, or credentials. Use environment variables and secret stores.
- Validate and sanitize all external input. Errors must be typed; no stack traces are exposed
  externally (ADR-015).

## Dependency and Supply-Chain Security

- Dependabot monitors Maven and GitHub Actions dependencies (see `dependabot.yml`).
- The CI pipeline runs a dependency/security scan (ADR-014).
- Review dependency updates promptly, prioritizing security advisories.
