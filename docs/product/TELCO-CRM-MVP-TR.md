**TELCO CRM PLATFORM**

Microservices Mimarisi ile Telekomünikasyon CRM Sistemi

_MVP Analiz ve Tasarım Dokümanı_

Eğitim Projesi - Öğrenci Yol Haritası

Java 21 • Spring Boot 3.x • Spring Cloud • PostgreSQL • Kafka • Docker • Kubernetes

Versiyon 1.0

# İçindekiler

1\. Proje Vizyonu ve Hedefler

2\. Domain Analizi: Telekom CRM Nedir?

3\. Aktörler ve Persona Tanımları

4\. Fonksiyonel Gereksinimler

5\. Fonksiyonel Olmayan Gereksinimler

6\. MVP Kapsamı (Scope In / Scope Out)

7\. Microservices Mimarisi - Servis Bazlı Bounded Context

8\. Servis Detayları

9\. Servisler Arası İletişim ve Event Akışı

10\. Veri Modeli - Yüksek Seviye Varlıklar

11\. Teknoloji Yığını

12\. API Tasarım Standartları

13\. Güvenlik Mimarisi

14\. Kabul Kriterleri (Acceptance Criteria)

15\. Sözlük (Glossary)

# 1\. Proje Vizyonu ve Hedefler

## 1.1 Vizyon

Telco CRM Platform, bir GSM operatörünün abonelerine yönelik tüm yaşam döngüsü süreçlerini (müşteri kaydı, ürün siparişi, faturalandırma, kullanım takibi, müşteri destek) tek bir microservices ekosistemi üzerinden yönetebilen, ölçeklenebilir ve event-driven bir CRM platformudur.

## 1.2 Eğitim Hedefleri

Bu proje öğrencilerin aşağıdaki konularda gerçek dünya seviyesinde yetkinlik kazanmasını hedefler:

- Domain-Driven Design (DDD) ile bounded context çıkarımı
- Spring Boot 3 ile production-grade microservices geliştirme
- Spring Cloud (Config, Gateway, Discovery) ile servis topolojisi yönetimi
- Apache Kafka ile asenkron event-driven entegrasyon
- REST + OpenAPI ile senkron iletişim ve sözleşme yönetimi
- PostgreSQL ile per-service veri tabanı (database-per-service pattern)
- Redis ile cache-aside ve idempotency stratejileri
- Docker Compose ile lokal orkestrasyon, Kubernetes ile production deployment
- Distributed tracing (OpenTelemetry / Zipkin) ve merkezi loglama
- JWT + OAuth2 ile API gateway seviyesinde güvenlik
- Resilience4j ile circuit breaker, retry ve bulkhead pattern uygulaması
- CI/CD pipeline kurulumu (GitHub Actions / GitLab CI)

## 1.3 İş Hedefleri (Senaryo)

Hayali operatörümüz "TelcoX", mevcut monolit CRM sistemini parça parça mikroservislere taşımak istemektedir. MVP kapsamında abone yaşam döngüsünün uçtan uca dijitalleştirilmesi, fatura üretiminin otomatize edilmesi ve self-servis kanalların (mobil/web) açılması hedeflenir.

# 2\. Domain Analizi: Telekom CRM Nedir?

Telekom CRM, bir operatörün müşteri ile olan tüm temas noktalarını (sales, service, support, billing) yöneten sistemler bütünüdür. Bankacılıkla benzer şekilde regülasyona tabi (BTK), yüksek hacimli ve gerçek zamanlı (CDR akışı) bir domaindir.

## 2.1 Telekom Özelinde Önemli Kavramlar

| **Kavram**    | **Açıklama**                                                                               |
| ------------- | ------------------------------------------------------------------------------------------ |
| MSISDN        | Mobile Subscriber ISDN - abonenin telefon numarası. Sistemde unique identifier rolü oynar. |
| IMSI          | International Mobile Subscriber Identity - SIM kart üzerindeki benzersiz kimlik.           |
| ICCID         | SIM kartın seri numarası.                                                                  |
| Subscription  | Bir müşterinin belirli bir tarife/pakete olan aktif aboneliği.                             |
| Tariff / Plan | Aboneye sunulan dakika, SMS, GB paketi. Postpaid ya da prepaid olabilir.                   |
| VAS           | Value Added Services - ek servisler (caller tunes, cloud, sigorta vb.).                    |
| CDR           | Call Detail Record - her arama/SMS/data kullanım kaydı. Faturalandırmanın temelidir.       |
| Top-up        | Prepaid hatlara TL yüklemesi.                                                              |
| MNP           | Mobile Number Portability - operatör değiştirme / numara taşıma.                           |
| BSCS / OCS    | Billing & Charging System - gerçek operatörde fatura motoru.                               |

## 2.2 Event Storming Çıktısı (Özet)

Aşağıdaki domain event'leri proje boyunca servisler arasında akacaktır:

- CustomerRegistered, CustomerKYCApproved, CustomerKYCRejected
- MSISDNAllocated, MSISDNReleased
- OrderCreated, OrderConfirmed, OrderCancelled
- SubscriptionActivated, SubscriptionSuspended, SubscriptionTerminated
- TariffChanged, AddonPurchased
- UsageRecorded, QuotaThresholdReached, QuotaExceeded
- InvoiceGenerated, PaymentReceived, PaymentFailed
- TicketOpened, TicketAssigned, TicketResolved
- NotificationDispatched

# 3\. Aktörler ve Persona Tanımları

| **Aktör**                 | **Rolü**               | **Tipik İşlemleri**                                                       |
| ------------------------- | ---------------------- | ------------------------------------------------------------------------- |
| Müşteri (Subscriber)      | Son kullanıcı abone    | Kayıt, sipariş, fatura görüntüleme, top-up, paket değişikliği, talep açma |
| Çağrı Merkezi Temsilcisi  | Customer support agent | Ticket çözme, abone bilgisi görme, manuel paket değiştirme                |
| Saha Bayisi               | Dealer / retailer      | Yeni abone aktivasyonu, SIM satışı, KYC ekran girişi                      |
| Pazarlama Yöneticisi      | Marketing manager      | Kampanya tanımlama, segment çıkarma                                       |
| Sistem Yöneticisi         | Admin                  | Tarife/ürün katalog yönetimi, kullanıcı yetkilendirme                     |
| Fatura Operatörü          | Billing operator       | Aylık fatura kesim job'larını izleme, fatura iptali                       |
| Sistem (Internal Service) | Service-to-service     | Event publish/consume, scheduled job, CDR mediation                       |

# 4\. Fonksiyonel Gereksinimler

Gereksinimler FR-XX kodu ile numaralandırılmıştır. Her gereksinimin karşılığında ilgili microservice ve ödev haftası belirtilmiştir.

## 4.1 Müşteri Yönetimi (Customer Service)

- FR-01: Sistem, bireysel ve kurumsal müşteri kaydını destekleyecektir (TCKN / VKN doğrulamalı).
- FR-02: KYC süreci sonrasında müşteri durumu PENDING → ACTIVE / REJECTED olarak güncellenir.
- FR-03: Müşteri adres, iletişim bilgisi ve kimlik belgesi yönetebilmelidir.
- FR-04: Müşteri silme işlemi soft-delete ile yapılır (GDPR/KVKK).

## 4.2 Ürün ve Tarife Kataloğu (Product Catalog Service)

- FR-05: Sistem tarife, paket, addon ve VAS ürünlerini hiyerarşik olarak yönetebilmelidir.
- FR-06: Her ürünün geçerlilik tarihleri (effectiveFrom / effectiveTo) ve hedef segmenti vardır.
- FR-07: Ürünler postpaid, prepaid, hybrid olarak sınıflandırılır.
- FR-08: Tarife değişiklikleri versiyonlanmalı, eski abonelerin tarifesi korunmalıdır.

## 4.3 Sipariş Yönetimi (Order Service)

- FR-09: Müşteri yeni hat siparişi, paket değişikliği veya addon siparişi verebilmelidir.
- FR-10: Siparişler saga pattern ile çoklu servis koordinasyonu içinde işlenir.
- FR-11: Sipariş durumları: DRAFT, PENDING_PAYMENT, PAID, FULFILLED, CANCELLED.
- FR-12: Sipariş iptalinde kompansasyon eventleri tetiklenir.

## 4.4 Abonelik Yönetimi (Subscription Service)

- FR-13: Sipariş tamamlandığında abonelik otomatik aktive edilir.
- FR-14: Abonelik askıya alma (ödeme yapılmadıysa), yeniden aktivasyon ve sonlandırma desteklenir.
- FR-15: Bir müşterinin birden fazla aboneliği olabilir.
- FR-16: Numara taşıma (MNP) için ayrı bir state machine yönetilir.

## 4.5 Kullanım Takibi (Usage Service)

- FR-17: CDR akışı Kafka üzerinden tüketilir ve kullanım bakiyeleri güncellenir.
- FR-18: Kalan kota (minutes, sms, mb) anlık görülebilir.
- FR-19: %80 ve %100 kullanım eşiklerinde notification eventi üretilir.
- FR-20: Aşım kullanımları billing servisine taşınmak üzere agregate edilir.

## 4.6 Faturalandırma (Billing Service)

- FR-21: Aylık bill-run job'u tüm postpaid aboneler için fatura keser.
- FR-22: Fatura kalemleri: aylık ücret, addon ücretleri, aşım, VAS ücretleri, vergiler.
- FR-23: Fatura PDF olarak üretilip Notification servisine gönderilir.
- FR-24: Ödeme alındığında InvoicePaid eventi üretilir.

## 4.7 Ödeme (Payment Service)

- FR-25: Kredi kartı, banka transferi ve cüzdan ile ödeme desteklenir.
- FR-26: Ödeme idempotent çalışır; aynı paymentRequestId iki kez işlenmez.
- FR-27: Başarısız ödemelerde 24/72/168 saat aralıkla retry tetiklenir.

## 4.8 Bildirim (Notification Service)

- FR-28: SMS, e-posta ve push notification kanallarını destekler.
- FR-29: Şablonlu (template) bildirim yönetimi vardır.
- FR-30: Kullanıcının iletişim tercihlerine (opt-in/opt-out) saygı gösterir.

## 4.9 Çağrı Merkezi Talep Yönetimi (Ticket Service)

- FR-31: Müşteriler şikayet, talep ve arıza kaydı açabilmelidir.
- FR-32: Ticket otomatik olarak ilgili ekibe SLA bazlı atanır.
- FR-33: Ticket durumu açıldığında müşteriye bildirim gider.

# 5\. Fonksiyonel Olmayan Gereksinimler

| **Kategori**       | **Gereksinim**          | **Hedef**                                      |
| ------------------ | ----------------------- | ---------------------------------------------- |
| Performans         | API yanıt süresi (p95)  | < 300 ms                                       |
| Performans         | Bill-run job süresi     | 100K abone < 30 dk                             |
| Ölçeklenebilirlik  | Yatay ölçeklenebilirlik | Stateless servisler, K8s HPA ile auto-scale    |
| Erişilebilirlik    | Servis uptime           | %99.5 (MVP)                                    |
| Güvenlik           | Auth                    | OAuth2 / JWT, gateway seviyesinde doğrulama    |
| Güvenlik           | Veri                    | PII alanlar şifreli saklanır (TCKN, kart no)   |
| Gözlemlenebilirlik | Distributed tracing     | OpenTelemetry + Zipkin/Jaeger                  |
| Gözlemlenebilirlik | Loglama                 | JSON yapılandırılmış log, merkezi (ELK / Loki) |
| Gözlemlenebilirlik | Metrikler               | Prometheus + Grafana                           |
| Dayanıklılık       | Circuit breaker         | Resilience4j tüm dış çağrılarda                |
| Veri Tutarlılığı   | Tutarlılık modeli       | Eventual consistency (Outbox pattern)          |
| Uyumluluk          | Regülasyon              | KVKK / GDPR, audit log zorunlu                 |

# 6\. MVP Kapsamı

## 6.1 Scope In (MVP'de Var)

- Bireysel müşteri kaydı ve KYC
- Postpaid tarife siparişi ve aktivasyon
- Aylık faturalama (sabit ücret + aşım)
- Kredi kartı ile ödeme (mock PSP)
- SMS ve e-posta bildirimleri (mock kanal)
- Kota görüntüleme ve eşik bildirimleri
- Temel müşteri ticketing
- Admin paneli için ürün katalog CRUD

## 6.2 Scope Out (MVP Sonrasına)

- Prepaid top-up ve gerçek zamanlı charging
- Numara taşıma (MNP) süreci
- Kurumsal müşteri ve filo yönetimi
- Kampanya / promosyon motoru
- BTK regülasyon raporları
- Roaming kullanım takibi
- Mobil uygulama (sadece backend + Swagger UI)

# 7\. Microservices Mimarisi

Aşağıdaki tablo MVP'de yer alacak servisleri, bounded context'lerini ve sahip oldukları temel agregatları listeler. Her servis kendi PostgreSQL şemasına sahiptir - database-per-service pattern uygulanır.

| **Servis**              | **Port** | **Bounded Context**    | **Temel Agregatlar**            |
| ----------------------- | -------- | ---------------------- | ------------------------------- |
| api-gateway             | 8080     | Edge routing           | -                               |
| discovery-server        | 8761     | Service registry       | -                               |
| config-server           | 8888     | Centralized config     | -                               |
| identity-service        | 9001     | Kimlik & yetki         | User, Role, Permission          |
| customer-service        | 9002     | Müşteri yönetimi       | Customer, Address, Document     |
| product-catalog-service | 9003     | Ürün kataloğu          | Tariff, Addon, ProductOffering  |
| order-service           | 9004     | Sipariş orkestrasyonu  | Order, OrderItem, SagaState     |
| subscription-service    | 9005     | Abonelik yaşam döngüsü | Subscription, MSISDN, SimCard   |
| usage-service           | 9006     | Kullanım & kota        | UsageRecord, Quota, CdrEvent    |
| billing-service         | 9007     | Fatura üretimi         | Invoice, InvoiceLine, BillCycle |
| payment-service         | 9008     | Ödeme                  | Payment, PaymentAttempt, Wallet |
| notification-service    | 9009     | Bildirim               | Notification, Template, Channel |
| ticket-service          | 9010     | Müşteri talepleri      | Ticket, Comment, SLA            |

## 7.1 Yatay Bileşenler (Infrastructure)

- PostgreSQL (her servis için ayrı schema veya ayrı DB instance)
- Apache Kafka - domain event broker
- Redis - cache + rate limiting + idempotency keys
- Keycloak (opsiyonel ileri seviye) - OAuth2 / OIDC sağlayıcı
- MinIO veya local FS - fatura PDF / belge depolama
- Zipkin + ELK + Prometheus + Grafana - observability stack

## 7.2 Mantıksal Mimari Şeması (Text)

\[ Web/Mobile Client \] | v +-----------------+ | API Gateway | <-- JWT validation, rate limit, routing +-----------------+ | | (REST) v +---------------------------------------------------------+ | Discovery Server | Config Server | +---------------------------------------------------------+ | ---------------------------------------------------------- | | | | | | v v v v v v identity customer catalog order subscription usage | | | | | | +--------+----------+----------+----------+----------+ | \[ Kafka Bus \] | +--------+----------+----------+----------+ | | | | | v v v v v billing payment notification ticket analytics(future)

# 8\. Servis Detayları

## 8.1 Customer Service

**Sorumluluk**

Müşterinin kimlik ve iletişim bilgilerinin master kaydı.

**Temel API'ler**

- POST /api/v1/customers - yeni müşteri
- GET /api/v1/customers/{id}
- PUT /api/v1/customers/{id}
- POST /api/v1/customers/{id}/documents - KYC belgesi yükleme
- POST /api/v1/customers/{id}/kyc/approve

**Event'ler**

- Publish: CustomerRegistered, CustomerKYCApproved, CustomerUpdated

## 8.2 Product Catalog Service

**Sorumluluk**

Tarife, addon, VAS ürünlerinin master katalog yönetimi. Read-heavy servis - Redis cache yoğun kullanılır.

**Temel API'ler**

- GET /api/v1/tariffs
- GET /api/v1/tariffs/{code}
- POST /api/v1/tariffs (admin)
- GET /api/v1/addons?tariffCode=...

**Event'ler**

- Publish: TariffCreated, TariffPriceChanged

## 8.3 Order Service

**Sorumluluk**

Sipariş alma ve Saga ile orchestrate etme. Customer → Catalog → Subscription → Payment zincirini yönetir.

**Temel API'ler**

- POST /api/v1/orders
- GET /api/v1/orders/{id}
- POST /api/v1/orders/{id}/cancel

**Event'ler**

- Publish: OrderCreated, OrderConfirmed, OrderCancelled
- Consume: PaymentCompleted, PaymentFailed, SubscriptionActivated

## 8.4 Subscription Service

**Sorumluluk**

Abonelik state machine'ini yönetir. MSISDN allocation/release.

**Temel API'ler**

- POST /api/v1/subscriptions (internal - Order tarafından çağrılır)
- GET /api/v1/subscriptions/{id}
- POST /api/v1/subscriptions/{id}/suspend
- POST /api/v1/subscriptions/{id}/reactivate
- POST /api/v1/subscriptions/{id}/terminate

**Event'ler**

- Publish: SubscriptionActivated, SubscriptionSuspended, SubscriptionTerminated
- Consume: OrderConfirmed, PaymentFailed (after grace period)

## 8.5 Usage Service

**Sorumluluk**

CDR (Call Detail Record) eventlerini tüketip kullanım sayaçlarını günceller. Write-heavy.

**Temel API'ler**

- GET /api/v1/usage/subscriptions/{id}/quota
- GET /api/v1/usage/subscriptions/{id}/history?from=...&to=...

**Event'ler**

- Consume: CdrRecorded (CDR simulator'dan)
- Publish: QuotaThresholdReached, QuotaExceeded

## 8.6 Billing Service

**Sorumluluk**

Aylık bill-run scheduler ve fatura üretimi.

**Temel API'ler**

- GET /api/v1/invoices?customerId=...
- GET /api/v1/invoices/{id}
- GET /api/v1/invoices/{id}/pdf
- POST /api/v1/billing/runs (admin trigger)

**Event'ler**

- Publish: InvoiceGenerated, InvoicePaid, InvoiceOverdue
- Consume: UsageAggregated, SubscriptionActivated, PaymentCompleted

## 8.7 Payment Service

**Sorumluluk**

Ödeme alma ve PSP entegrasyonu (mock).

**Temel API'ler**

- POST /api/v1/payments
- GET /api/v1/payments/{id}
- POST /api/v1/payments/{id}/refund

**Event'ler**

- Publish: PaymentCompleted, PaymentFailed, PaymentRefunded
- Consume: InvoiceGenerated (auto-pay senaryosu için)

## 8.8 Notification Service

**Sorumluluk**

Çok kanallı bildirim gönderimi.

**Temel API'ler**

- POST /api/v1/notifications (internal)
- GET /api/v1/notifications/users/{id}/history

**Event'ler**

- Consume: ALMOST tüm domain eventler (template tabanlı eşleştirme)

## 8.9 Ticket Service

**Sorumluluk**

Müşteri talep / şikayet yönetimi, SLA.

**Temel API'ler**

- POST /api/v1/tickets
- GET /api/v1/tickets/{id}
- POST /api/v1/tickets/{id}/comments
- POST /api/v1/tickets/{id}/assign
- POST /api/v1/tickets/{id}/resolve

**Event'ler**

- Publish: TicketOpened, TicketResolved, SlaBreached

# 9\. Servisler Arası İletişim ve Event Akışı

## 9.1 Senkron vs Asenkron Kararı

| **Senaryo**                               | **İletişim Tipi**      | **Gerekçe**                                       |
| ----------------------------------------- | ---------------------- | ------------------------------------------------- |
| Order oluştururken Customer kontrolü      | Senkron (REST)         | İmmediat doğrulama gerekli                        |
| Order oluştururken katalog ürün fiyatı    | Senkron (REST + cache) | Snapshot alınmalı                                 |
| Order → Subscription aktivasyonu          | Asenkron (Kafka)       | Geri alınabilir, eventual consistency             |
| Subscription → Billing'e abonelik bilgisi | Asenkron (Kafka)       | Loose coupling                                    |
| CDR akışı → Usage                         | Asenkron (Kafka)       | Yüksek hacim, geriye dönük işlenebilir            |
| Invoice → Notification                    | Asenkron (Kafka)       | Notification fail olsa bile fatura kesilmiş kalır |
| Payment doğrulama                         | Senkron (PSP REST)     | Müşteriye anlık geri dönüş gerekli                |

## 9.2 Saga Örneği: Yeni Hat Siparişi

1\. Customer --POST /orders--> Order Service 2. Order Service: OrderCreated ===> Kafka 3. Payment Service consume OrderCreated -> charge attempt -> PaymentCompleted ===> Kafka 4. Subscription Service consume PaymentCompleted -> allocate MSISDN -> create Subscription -> SubscriptionActivated ===> Kafka 5. Order Service consume SubscriptionActivated -> mark order FULFILLED 6. Notification Service consume SubscriptionActivated -> send welcome SMS Kompansasyon: Eğer SubscriptionActivation fail olursa -> Subscription Service: SubscriptionActivationFailed -> Payment Service: refund tetiklenir -> Order Service: order CANCELLED'a düşer

## 9.3 Outbox Pattern Zorunluluğu

Bir servis DB'sine yazma + Kafka publish atomik olmalıdır. Bunu sağlamak için her serviste outbox tablosu tutulur, ayrı bir publisher worker bu tabloyu Kafka'ya gönderir. Transactional outbox + idempotent consumer kombinasyonu MVP'de zorunludur.

# 10\. Veri Modeli - Yüksek Seviye Varlıklar

Aşağıda her servisin sahip olduğu temel varlıkların öz tanımı verilmiştir. Detaylı ER diyagramı çıkarımı Hafta 1 ödevidir.

## 10.1 Customer Service

- Customer(id, type\[INDIVIDUAL|CORPORATE\], firstName, lastName, identityNumber, dateOfBirth, status, createdAt)
- Address(id, customerId, line1, city, district, postalCode, isDefault)
- Document(id, customerId, type\[ID_CARD|PASSPORT\], fileRef, verifiedAt)

## 10.2 Product Catalog

- Tariff(id, code, name, type\[POSTPAID|PREPAID\], monthlyFee, minutesIncluded, smsIncluded, dataMbIncluded, status, effectiveFrom, effectiveTo)
- Addon(id, code, name, price, type\[DATA|SMS|MINUTES|VAS\], validityDays)
- TariffAddon(tariffId, addonId) - many-to-many

## 10.3 Order Service

- Order(id, customerId, status, totalAmount, currency, createdAt)
- OrderItem(id, orderId, productCode, productType, quantity, unitPrice)
- SagaState(id, orderId, currentStep, payload, lastUpdated)

## 10.4 Subscription Service

- Subscription(id, customerId, msisdn, tariffCode, status\[ACTIVE|SUSPENDED|TERMINATED\], activatedAt, terminatedAt)
- MsisdnPool(msisdn, status\[FREE|RESERVED|ALLOCATED\], reservedUntil)
- SimCard(iccid, imsi, msisdn, status)

## 10.5 Usage Service

- Quota(id, subscriptionId, periodStart, periodEnd, minutesRemaining, smsRemaining, mbRemaining)
- UsageRecord(id, subscriptionId, type\[VOICE|SMS|DATA\], quantity, recordedAt, cdrRef)

## 10.6 Billing Service

- Invoice(id, customerId, subscriptionId, periodStart, periodEnd, subTotal, tax, grandTotal, status, dueDate, issuedAt)
- InvoiceLine(id, invoiceId, description, quantity, unitPrice, lineTotal)
- BillCycle(id, customerId, dayOfMonth, nextRunDate)

## 10.7 Payment Service

- Payment(id, invoiceId, amount, method, status, externalRef, paidAt)
- PaymentAttempt(id, paymentId, attemptNo, response, attemptedAt)

## 10.8 Notification Service

- NotificationTemplate(id, code, channel, locale, subject, bodyTemplate)
- Notification(id, userId, templateCode, channel, payloadJson, status, sentAt)

## 10.9 Ticket Service

- Ticket(id, customerId, category, priority, status, slaDueAt, createdAt)
- TicketComment(id, ticketId, authorId, body, createdAt)

# 11\. Teknoloji Yığını

| **Katman**    | **Teknoloji**                                 | **Sürüm / Not**                            |
| ------------- | --------------------------------------------- | ------------------------------------------ |
| Dil           | Java                                          | 21 (LTS)                                   |
| Framework     | Spring Boot                                   | 3.3.x                                      |
| Spring Cloud  | Gateway, Config, Eureka/Consul, OpenFeign     | 2023.0.x                                   |
| Build         | Maven veya Gradle                             | Maven Multi-module önerilir                |
| DB            | PostgreSQL                                    | 16, her servis için ayrı schema            |
| Cache         | Redis                                         | 7                                          |
| Broker        | Apache Kafka                                  | 3.7+ (KRaft mode)                          |
| Migration     | Flyway                                        | Her serviste                               |
| ORM           | Spring Data JPA + Hibernate                   | -                                          |
| Mapping       | MapStruct                                     | -                                          |
| Validation    | Jakarta Bean Validation                       | -                                          |
| Auth          | Spring Security + JWT (jjwt 0.12.x)           | Gateway'de relay                           |
| Doc           | Springdoc OpenAPI                             | Her servis ayrı Swagger UI                 |
| Resilience    | Resilience4j                                  | Circuit breaker, retry, bulkhead           |
| Observability | Micrometer + OpenTelemetry + Zipkin           | -                                          |
| Test          | JUnit 5, Mockito, Testcontainers, RestAssured | -                                          |
| Container     | Docker, Docker Compose                        | Lokal geliştirme                           |
| Orchestration | Kubernetes                                    | Minikube / Kind ile lokal                  |
| CI/CD         | GitHub Actions                                | Build → test → docker push → kubectl apply |

# 12\. API Tasarım Standartları

- Tüm REST API'ler /api/v1 prefix kullanır. Versiyonlama URI bazlıdır.
- Resource isimleri çoğul (customers, orders, subscriptions).
- HTTP method semantiği: GET (read), POST (create + komut), PUT (full update), PATCH (partial), DELETE (soft delete).
- Hata formatı RFC 7807 Problem Details standardına uyar.
- Pagination: ?page=0&size=20&sort=createdAt,desc - Spring Data Pageable kullanılır.
- Idempotency-Key header POST işlemlerinde desteklenir (özellikle Payment, Order).
- Correlation-Id header gateway tarafından enjekte edilir, tüm servislerde log'lara yazılır.
- Tüm tarih alanları ISO-8601 UTC formatındadır.
- Para alanları için BigDecimal + currency code (TRY) ayrı alan olarak tutulur.

## 12.1 Örnek Hata Cevabı

{ "type": "<https://telco.example/errors/customer-not-found>", "title": "Customer not found", "status": 404, "detail": "Customer with id 1234 does not exist", "instance": "/api/v1/customers/1234", "correlationId": "9f3c1b..." }

# 13\. Güvenlik Mimarisi

- Auth: identity-service login üzerinden JWT (access + refresh) üretir.
- API Gateway her isteği JWT ile doğrular, payload'dan userId/role çıkarır ve downstream'e X-User-Id, X-User-Roles header'ları olarak iletir.
- Servisler kendi içinde JWT'yi tekrar doğrulamaz; gateway behind trust kullanılır. (Production'da mTLS önerilir, MVP'de scope dışı.)
- Refresh token rotation: her refresh sonrası eski token blacklist'e (Redis) eklenir, reuse tespit edilirse tüm aktif tokenlar iptal edilir.
- Yetkilendirme: @PreAuthorize ile role/permission bazlı; özellikle admin endpoint'leri için.
- PII şifreleme: TCKN ve kart no AES-GCM ile şifrelenir, key Vault/K8s Secret'tan okunur.
- Audit log: identity, customer, payment ve subscription servislerinde her değişiklik audit_log tablosuna yazılır.
- Rate limit: Gateway'de Redis tabanlı; user başına 100 req/min varsayılan.

# 14\. Kabul Kriterleri

MVP teslimde aşağıdaki senaryoların uçtan uca çalışıyor olması gerekir:

## 14.1 Senaryo: Yeni Abone Onboarding

- Müşteri başvurusu yapılır (POST /customers).
- KYC belgesi yüklenir ve admin tarafından onaylanır.
- Müşteri postpaid bir tarife seçip sipariş verir.
- Mock PSP ile ödeme başarılı olur.
- Subscription otomatik aktive olur, MSISDN atanır.
- Müşteriye welcome SMS (mock log) gider.

## 14.2 Senaryo: Aylık Fatura

- Bill-run job manual tetiklenir.
- Aktif abonelerin son 1 aylık usage'i agregate edilir.
- Her abone için invoice oluşur, PDF üretilir.
- InvoiceGenerated eventi ile notification servisi e-posta atar.
- Müşteri faturasını ödediğinde InvoicePaid eventi tetiklenir.

## 14.3 Senaryo: Kota Aşımı

- CDR simulator usage event'leri üretir.
- Usage service kotaları azaltır.
- %80'de uyarı SMS'i gider.
- %100'de ek paket önerisi içeren SMS gider.
- Aşım sonrası kullanım billing'e overage olarak gider.

# 15\. Sözlük (Glossary)

| **Terim**       | **Tanım**                                                                     |
| --------------- | ----------------------------------------------------------------------------- |
| Bounded Context | DDD'de bir modelin geçerli olduğu açıkça tanımlı sınır.                       |
| Saga            | Distributed transaction'ları yöneten, kompansasyon adımları içeren pattern.   |
| Outbox Pattern  | DB transaction + message publish'i atomik hale getiren tablo bazlı çözüm.     |
| Idempotency     | Aynı işlemin birden fazla kez yapılmasının sonucu değiştirmemesi.             |
| CQRS            | Command (yazma) ve Query (okuma) sorumluluklarının ayrılması.                 |
| Circuit Breaker | Hata oranı eşiği aşıldığında çağrıları otomatik kesen pattern (Resilience4j). |
| CDR             | Call Detail Record - telekom kullanım kayıtları.                              |
| MSISDN          | Abonenin telefon numarası, sistem-genelinde unique.                           |
| MNP             | Mobile Number Portability - numara taşıma.                                    |
| KYC             | Know Your Customer - kimlik doğrulama süreci.                                 |
| VAS             | Value Added Service - ek servis (cloud, müzik vb.).                           |
| KVKK / GDPR     | Kişisel verilerin korunmasına dair regülasyonlar.                             |
| Service Mesh    | Servisler arası iletişimi yöneten infrastructure katmanı (Istio vb.).         |
| HPA             | Horizontal Pod Autoscaler - K8s yatay ölçeklendirme.                          |

_- Doküman Sonu -_