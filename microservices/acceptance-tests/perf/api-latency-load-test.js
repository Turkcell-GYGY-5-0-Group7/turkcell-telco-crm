/**
 * Sprint 14 - Feature 14.3.1: API latency load test (NFR-01).
 *
 * Validates that p95 API response time stays under 300ms under a sustained, realistic mixed
 * read/write load driven through the real API gateway with real Keycloak-issued JWTs - the same
 * trust chain the acceptance suite exercises (see
 * microservices/acceptance-tests/src/test/java/com/telco/acceptance/support/TokenProvider.java and
 * AcceptanceConfig.java, whose defaults this script mirrors so it runs unmodified against the same
 * local docker compose stack).
 *
 * Endpoint mix per iteration (representative of a real subscriber session, per
 * docs/api-contracts/{product-catalog,order,subscription}-service.md):
 *   1. GET  /api/v1/tariffs                            - list, Redis cache-aside read
 *   2. GET  /api/v1/tariffs/{code}                      - single tariff, cache-aside read
 *   3. GET  /api/v1/orders/customer/{customerId}        - list a customer's orders
 *   4. GET  /api/v1/subscriptions?customerId=...        - list a customer's subscriptions
 *   5. POST /api/v1/orders                              - lighter write: place a new order
 *
 * Fixtures (tariffs, KYC-approved customers) are seeded once in setup() so the write path exercises
 * a real ACTIVE customer without paying saga/KYC latency inside the timed load itself - order
 * capture validates the customer's KYC status synchronously (order-service contract).
 *
 * IDENTITY POOL (fixes the rate-limiter blocker found in the first run of this script): the
 * gateway enforces a fixed-window rate limit of 100 requests/minute per authenticated JWT subject
 * (RateLimitingFilter, NFR-18). The first run of this script shared a single seeded SUBSCRIBER
 * identity (subscriber@telco.local) across every VU, so sustained concurrency above a handful of
 * VUs saturated that one subject's 100 req/min budget within seconds and the gateway legitimately
 * returned 429 for the rest of each 1-minute window - a correct NFR-18 outcome, but one that made
 * this an unusable NFR-01 measurement. This is now fixed by provisioning 30 dedicated,
 * load-test-only SUBSCRIBER identities (loadtest-user-01@telco.local .. loadtest-user-30@telco.local,
 * password "loadtest", infra/docker/keycloak/realm/realm-export.json) and round-robining VUs across
 * them (one identity per VU for up to 30 VUs), so each identity's own request rate stays well under
 * the 100 req/min budget. These identities are test infrastructure only - never used outside this
 * load test.
 *
 * RESIDUAL, OUT-OF-SCOPE CONSTRAINT: the two ownership-gapped reads below (orders-by-customer,
 * subscriptions-by-customer) still authenticate with the single shared ADMIN identity
 * (admin@telco.local), per the same identity-linkage gap documented in AcceptanceConfig.java
 * (no Keycloak sub -> customerId mapping exists, so no SUBSCRIBER token can list an arbitrary
 * seeded customer's orders/subscriptions). Provisioning a matching pool of dedicated ADMIN
 * identities was evaluated and is explicitly out of scope for this task (SUBSCRIBER-only
 * identities were authorized) - so those two endpoints can still be rate-limited under this
 * shared identity at higher concurrency. This script's NFR-01 threshold is scoped to
 * `expected_response:true` (k6's built-in 2xx/3xx classification) so a 429 on those two endpoints
 * is excluded from the latency gate rather than corrupting it; 429 volume is reported separately
 * (http_req_failed / per-check breakdown) as a non-blocking finding for tech-lead/platform-engineer
 * follow-up.
 *
 * IMPORTANT - run this against `localhost`, not a k6 container on the compose network:
 * microservices/configs/application-dev.yml pins `telco.platform.security.jwt.issuer` to the
 * literal string `http://localhost:8085/realms/telco-crm` and verifies every token against a
 * statically configured RSA public key (JwtService#requireIssuer), rather than doing per-request
 * JWKS/issuer discovery. Keycloak stamps the token's `iss` claim from the Host header used to reach
 * it, so a token fetched via a container DNS name (e.g. `telco-keycloak:8080`) carries a different
 * `iss` and is rejected with 401 by every downstream domain service (confirmed empirically while
 * building this script - the gateway itself authenticates such a token fine since its own decoder
 * never checks issuer, but each downstream service's platform-starter JwtService does). Running k6
 * on the host against `http://localhost:8080` / `http://localhost:8085` (this script's defaults,
 * matching AcceptanceConfig.java) reproduces the exact request shape the acceptance suite already
 * uses successfully.
 *
 * Run (from repo root, k6 installed locally via `brew install k6`; see docs/tasks/
 * sprint-14-testing-and-hardening/14.3.1-api-latency-load-test-report.md for the exact command used):
 *
 *   k6 run microservices/acceptance-tests/perf/api-latency-load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

// ---- Configuration (env-overridable; defaults match AcceptanceConfig.java's host-side view,
// i.e. localhost ports mapped by docker compose) ----
const GATEWAY_BASE_URL = __ENV.GATEWAY_BASE_URL || 'http://localhost:8080';
const KEYCLOAK_TOKEN_URI = __ENV.KEYCLOAK_TOKEN_URI || 'http://localhost:8085/realms/telco-crm/protocol/openid-connect/token';
const KEYCLOAK_CLIENT_ID = __ENV.KEYCLOAK_CLIENT_ID || 'telco-web';
const ADMIN_USERNAME = __ENV.KEYCLOAK_ADMIN_USERNAME || 'admin@telco.local';
const ADMIN_PASSWORD = __ENV.KEYCLOAK_ADMIN_PASSWORD || 'admin';
const SUBSCRIBER_USERNAME = __ENV.KEYCLOAK_SUBSCRIBER_USERNAME || 'subscriber@telco.local';
const SUBSCRIBER_PASSWORD = __ENV.KEYCLOAK_SUBSCRIBER_PASSWORD || 'subscriber';

// Dedicated load-test-only SUBSCRIBER identity pool (infra/docker/keycloak/realm/realm-export.json),
// round-robined one-per-VU below so no single JWT subject's request rate approaches the gateway's
// 100 req/min per-subject limit (see the IDENTITY POOL note in the file header).
const LOADTEST_USER_POOL_SIZE = Number(__ENV.LOADTEST_USER_POOL_SIZE || 30);
const LOADTEST_USER_PASSWORD = __ENV.LOADTEST_USER_PASSWORD || 'loadtest';
function loadtestUsername(n) {
  return `loadtest-user-${String(n).padStart(2, '0')}@telco.local`;
}

// Target load: 30 concurrent VUs sustained for 90s (with a 20s ramp-up and 10s ramp-down either
// side), ~2 minutes total. 30 VUs is a deliberately moderate concurrency figure for a single-node
// local docker-compose stack (NFR-01 states the <300ms p95 target but does not pin a concurrency
// figure) - enough to keep every service's connection pool and the gateway's route table under
// genuine concurrent pressure without saturating a laptop-class Docker Desktop VM to the point the
// test would be measuring host contention rather than API latency.
export const options = {
  scenarios: {
    sustained_mixed_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 30 },
        { duration: '90s', target: 30 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  setupTimeout: '120s',
  thresholds: {
    // Primary NFR-01 gate: p95 latency of requests the gateway actually let through (not
    // rate-limited) - see the "RESIDUAL, OUT-OF-SCOPE CONSTRAINT" note above (the two
    // ADMIN-gated reads can still be rate-limited under the single shared ADMIN identity).
    'http_req_duration{expected_response:true}': ['p(95)<300'],
    // Informational, non-blocking: overall p95 including any 429s from the shared ADMIN
    // identity, kept for visibility in the summary but not used as the pass/fail gate.
    'http_req_duration': ['p(95)<1000'],
  },
};

function fetchToken(username, password) {
  const res = http.post(
    KEYCLOAK_TOKEN_URI,
    {
      grant_type: 'password',
      client_id: KEYCLOAK_CLIENT_ID,
      username: username,
      password: password,
      scope: 'openid',
    },
    { tags: { name: 'POST /token (setup)' } },
  );
  if (res.status !== 200) {
    throw new Error(`token fetch failed for ${username}: ${res.status} ${res.body}`);
  }
  return res.json('access_token');
}

function authHeaders(token, extra) {
  return Object.assign({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, extra || {});
}

// Independent re-implementation of the public TCKN checksum algorithm, matching
// com.telco.acceptance.support.TurkishIdGenerator (customer-service validates identityNumber
// against this same public checksum spec at registration).
let tcknSeed = Math.floor(Math.random() * 1000000);
function nextTurkishId() {
  const seed = ++tcknSeed;
  const d = new Array(9);
  d[0] = 1 + (seed % 8);
  for (let i = 1; i < 9; i++) {
    d[i] = Math.floor(seed / (i + 1)) % 10;
  }
  const oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
  const evenSum = d[1] + d[3] + d[5] + d[7];
  const check10 = (((oddSum * 7 - evenSum) % 10) + 10) % 10;
  const check11 = (oddSum + evenSum + check10) % 10;
  return d.join('') + String(check10) + String(check11);
}

const NUM_TARIFFS = 5;
const NUM_CUSTOMERS = 15;

export function setup() {
  const adminToken = fetchToken(ADMIN_USERNAME, ADMIN_PASSWORD);
  const subscriberToken = fetchToken(SUBSCRIBER_USERNAME, SUBSCRIBER_PASSWORD);

  // One dedicated token per pooled load-test identity, fetched once here so the sustained-load
  // phase below never pays token-fetch latency and never shares a JWT subject across VUs.
  const loadtestTokens = [];
  for (let i = 1; i <= LOADTEST_USER_POOL_SIZE; i++) {
    loadtestTokens.push(fetchToken(loadtestUsername(i), LOADTEST_USER_PASSWORD));
  }

  // Seed tariffs (ADMIN-authored catalog entries, TariffController.createTariff) reused for both
  // the read mix (list/by-code) and as the order item in the write mix.
  const tariffs = [];
  for (let i = 0; i < NUM_TARIFFS; i++) {
    const code = `PERF-${Date.now()}-${i}-${Math.floor(Math.random() * 100000)}`;
    const body = JSON.stringify({
      code: code,
      name: 'Perf Postpaid',
      type: 'POSTPAID',
      monthlyFee: 49.9,
      currency: 'TRY',
      minutesIncluded: 1000,
      smsIncluded: 1000,
      dataMbIncluded: 5000,
      targetSegment: 'perf-load-test',
      effectiveFrom: new Date().toISOString(),
    });
    const res = http.post(`${GATEWAY_BASE_URL}/api/v1/tariffs`, body, { headers: authHeaders(adminToken) });
    if (res.status !== 201) {
      throw new Error(`tariff creation failed: ${res.status} ${res.body}`);
    }
    const data = res.json('data');
    tariffs.push({ id: data.id, code: data.code });
  }

  // Seed KYC-approved (ACTIVE) customers so the write mix (POST /orders) hits real, valid
  // customers - order capture validates customer ACTIVE/KYC status synchronously
  // (docs/api-contracts/order-service.md), so an unapproved customer would 4xx on every write.
  const customerIds = [];
  for (let i = 0; i < NUM_CUSTOMERS; i++) {
    const registerBody = JSON.stringify({
      type: 'INDIVIDUAL',
      firstName: 'Perf',
      lastName: `Load${i}`,
      identityNumber: nextTurkishId(),
      dateOfBirth: '1990-01-01',
    });
    const registerRes = http.post(`${GATEWAY_BASE_URL}/api/v1/customers`, registerBody, {
      headers: authHeaders(subscriberToken),
    });
    if (registerRes.status !== 201) {
      throw new Error(`customer registration failed: ${registerRes.status} ${registerRes.body}`);
    }
    const customerId = registerRes.json('data.id');

    const uploadRes = http.post(
      `${GATEWAY_BASE_URL}/api/v1/customers/${customerId}/documents`,
      {
        type: 'ID_CARD',
        file: http.file('perf-load-test-fake-id-scan', 'id-card.pdf', 'application/pdf'),
      },
      { headers: { Authorization: `Bearer ${subscriberToken}` } },
    );
    if (uploadRes.status !== 201) {
      throw new Error(`KYC document upload failed: ${uploadRes.status} ${uploadRes.body}`);
    }

    const approveRes = http.post(`${GATEWAY_BASE_URL}/api/v1/customers/${customerId}/kyc/approve`, null, {
      headers: authHeaders(adminToken),
    });
    if (approveRes.status !== 200) {
      throw new Error(`KYC approval failed: ${approveRes.status} ${approveRes.body}`);
    }

    customerIds.push(customerId);
  }

  return { adminToken, subscriberToken, loadtestTokens, tariffs, customerIds };
}

export default function (data) {
  const { adminToken, loadtestTokens, tariffs, customerIds } = data;
  // One pooled identity per VU (round-robin if VUs ever exceed the pool size) - see the IDENTITY
  // POOL note in the file header. __VU is 1-based in k6.
  const vuToken = loadtestTokens[(__VU - 1) % loadtestTokens.length];
  const tariff = tariffs[Math.floor(Math.random() * tariffs.length)];
  const customerId = customerIds[(__VU + __ITER) % customerIds.length];

  // 1. GET /api/v1/tariffs - list (paged, Redis cache-served read).
  let res = http.get(`${GATEWAY_BASE_URL}/api/v1/tariffs`, {
    headers: authHeaders(vuToken),
    tags: { name: 'GET /tariffs (list)' },
  });
  check(res, { 'tariffs list -> 200': (r) => r.status === 200 });

  // 2. GET /api/v1/tariffs/{code} - single tariff, cache-aside read.
  res = http.get(`${GATEWAY_BASE_URL}/api/v1/tariffs/${tariff.code}`, {
    headers: authHeaders(vuToken),
    tags: { name: 'GET /tariffs/{code}' },
  });
  check(res, { 'tariff by code -> 200': (r) => r.status === 200 });

  // 3. GET /api/v1/orders/customer/{customerId} - list a customer's orders (paged). ADMIN token:
  // OrderController.getOrdersByCustomer enforces ownership unless the caller is ADMIN, and the
  // path here is customerId-keyed (not "the caller's own orders"), so ADMIN is used to read
  // orders for an arbitrary seeded customer, consistent with the other ADMIN-gated reads below.
  res = http.get(`${GATEWAY_BASE_URL}/api/v1/orders/customer/${customerId}`, {
    headers: authHeaders(adminToken),
    tags: { name: 'GET /orders/customer/{customerId} (list)' },
  });
  check(res, { 'orders list -> 200': (r) => r.status === 200 });

  // 4. GET /api/v1/subscriptions?customerId=... - list a customer's subscriptions. ADMIN token:
  // the ownership-linkage gap documented on GatewayApi#getSubscriptionsByCustomer means a
  // SUBSCRIBER token can never satisfy `customerId == JWT sub` for a suite-created customer.
  res = http.get(`${GATEWAY_BASE_URL}/api/v1/subscriptions?customerId=${customerId}`, {
    headers: authHeaders(adminToken),
    tags: { name: 'GET /subscriptions (list)' },
  });
  check(res, { 'subscriptions list -> 200': (r) => r.status === 200 });

  // 5. POST /api/v1/orders - lighter write: place a new order against a seeded ACTIVE customer.
  const idempotencyKey = `perf-${__VU}-${__ITER}-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
  const orderBody = JSON.stringify({
    customerId: customerId,
    items: [{ tariffId: tariff.id, quantity: 1 }],
  });
  res = http.post(`${GATEWAY_BASE_URL}/api/v1/orders`, orderBody, {
    headers: authHeaders(vuToken, { 'Idempotency-Key': idempotencyKey }),
    tags: { name: 'POST /orders' },
  });
  check(res, { 'order created -> 201': (r) => r.status === 201 });

  sleep(1);
}
