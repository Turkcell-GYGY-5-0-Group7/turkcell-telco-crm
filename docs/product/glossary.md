# Glossary

## Telco CRM Platform

| Field | Value |
| --- | --- |
| Document | Bilingual Domain and Architecture Glossary |
| Version | 1.0 |
| Parent | [BRD.md](./BRD.md) |
| Last updated | 2026-06-19 |

Telecom domain terms are kept in their industry-standard form. Turkish regulatory and
identity terms are retained because they are domain-specific (KVKK, TCKN, VKN, BTK).

---

## 1. Telecom Domain Terms

| Term | Turkish | Definition |
| --- | --- | --- |
| MSISDN | Abone numarasi | Mobile Subscriber ISDN. The subscriber phone number; a system-wide unique identifier. |
| IMSI | - | International Mobile Subscriber Identity. Unique identity stored on the SIM. |
| ICCID | SIM seri numarasi | SIM card serial number. |
| Subscription | Abonelik | A customer's active membership to a specific tariff/plan. |
| Tariff / Plan | Tarife / Paket | The minutes, SMS, and data package offered to a subscriber. |
| VAS | Katma degerli servis | Value Added Service (caller tunes, cloud, insurance, etc.). |
| CDR | Arama detay kaydi | Call Detail Record. A single call/SMS/data usage event; the basis for billing. |
| Top-up | Bakiye yukleme | Balance loading for prepaid lines (post-MVP). |
| MNP | Numara tasima | Mobile Number Portability; operator change / number transfer (post-MVP). |
| BSCS / OCS | Faturalama motoru | Billing and Charging System; the real-operator billing engine. |
| Overage | Asim | Usage beyond the included quota, charged on the next invoice. |
| Quota | Kota | Remaining included allowance (minutes, SMS, MB) for a period. |
| Postpaid | Faturali | Pay-after-use billing model. |
| Prepaid | On odemeli | Pay-before-use balance model (post-MVP). |

## 2. Identity and Regulatory Terms

| Term | Turkish | Definition |
| --- | --- | --- |
| TCKN | T.C. Kimlik No | Turkish national identity number for individuals. |
| VKN | Vergi Kimlik No | Turkish tax identity number for corporations. |
| KYC | Kimlik dogrulama | Know Your Customer; identity verification process. |
| KVKK | KVKK | Turkish Personal Data Protection Law (analogous to GDPR). |
| GDPR | GDPR | EU General Data Protection Regulation. |
| BTK | BTK | Turkish Information and Communication Technologies Authority (telecom regulator). |
| PII | Kisisel veri | Personally Identifiable Information. |

## 3. Architecture and Platform Terms

| Term | Definition |
| --- | --- |
| Bounded Context | A clearly delimited boundary within which a domain model is valid (DDD). |
| Saga | A pattern coordinating a distributed transaction with compensation steps. |
| Outbox Pattern | A table-based technique making a DB transaction and message publish atomic. |
| Inbox Pattern | Consumer-side deduplication to guarantee idempotent event processing. |
| Idempotency | The property that repeating an operation does not change the result. |
| CQRS | Command Query Responsibility Segregation; separating write and read responsibilities. |
| Mediator | A single dispatch entry point routing requests through pipeline behaviors to handlers. |
| Pipeline Behavior | A cross-cutting step (validation, security, logging, transaction) in the mediator flow. |
| Circuit Breaker | A pattern that stops calls automatically when an error threshold is exceeded (Resilience4j). |
| Eventual Consistency | A consistency model where replicas converge over time, not instantly. |
| Schema Registry | A service that stores and governs Avro event schemas and compatibility rules. |
| Service Mesh | Infrastructure layer managing service-to-service communication (e.g. Istio); post-MVP. |
| HPA | Horizontal Pod Autoscaler; Kubernetes horizontal scaling. |
| BFF | Backend For Frontend; an aggregation layer tailored to a client. |
| ApiResult | The standard response wrapper for all external APIs (ADR-015). |

---

Document end.
