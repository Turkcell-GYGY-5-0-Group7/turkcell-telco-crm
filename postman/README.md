# Telco CRM — Postman Collections

Two collections, two environments, and a one-time hosts-file setup script.

## Directory layout

```
postman/
├── collections/
│   ├── Telco-CRM-Via-Gateway.postman_collection.json   ← all traffic through api.localhost:8080
│   └── Telco-CRM-Direct-Services.postman_collection.json ← each service on its own port
├── environments/
│   ├── Local-Gateway.postman_environment.json           ← pair with the gateway collection
│   └── Local-Direct.postman_environment.json            ← pair with the direct collection
└── scripts/
    ├── setup-hosts.sh    ← macOS / Linux (run once)
    └── setup-hosts.ps1   ← Windows (run once as Administrator)
```

## Quick start

### 1. One-time hosts setup (gateway collection only)

macOS / Linux:
```bash
chmod +x postman/scripts/setup-hosts.sh
./postman/scripts/setup-hosts.sh
```

Windows (PowerShell as Administrator):
```powershell
.\postman\scripts\setup-hosts.ps1
```

This adds `127.0.0.1  api.localhost` to your hosts file so the gateway is reachable at
`http://api.localhost:8080`.

### 2. Import into Postman

1. Open Postman → **Import**
2. Drag-and-drop (or select) both collection files from `postman/collections/`
3. Drag-and-drop both environment files from `postman/environments/`

### 3. Start the stack

```bash
make infra-up        # Starts Keycloak, Postgres, Redis, Kafka, Mongo, MinIO
make services-up     # Starts all microservices + the API gateway on :8080
```

### 4. Select the right environment

| Collection | Environment |
|---|---|
| Telco CRM — Via Gateway | Local - Gateway (api.localhost:8080) |
| Telco CRM — Direct Services | Local - Direct (per-service ports) |

### 5. Fill in credentials

In the selected environment, set:
- `keycloak_username` — a Keycloak user in the `telco-crm` realm (default: `admin@telco.com`)
- `keycloak_password` — that user's password
- `keycloak_client_id` — the confidential client configured for API access (default: `telco-crm-app`)

The collection pre-request script auto-fetches a JWT from Keycloak before the first request and
refreshes it 30 s before expiry. You never need to paste tokens manually.

## Collections

### Telco CRM — Via Gateway

Routes all traffic through `{{gateway_url}}` (= `http://api.localhost:8080`).

**Top-level folders:**

| Folder | Contents |
|---|---|
| `_Auth` | Explicit token request — useful for troubleshooting auth |
| `By Service` | One sub-folder per microservice; all endpoints |
| `Journeys` | Four end-to-end flows with chained variables |

**Journeys:**

| Journey | Steps |
|---|---|
| 1. Customer Onboarding | Register → KYC approve → pick tariff → place order → pay → confirm subscription |
| 2. Invoice & Payment | Trigger bill run → list invoices → view detail → pay → download PDF |
| 3. Usage Monitoring | Get quota → cursor-paginated CDR history |
| 4. Support Ticket Flow | Open → comment → assign → resolve → verify |

Run journey folders top-to-bottom. Test scripts automatically chain IDs:
`customer_id` → `order_id` → `subscription_id` → `payment_id` → `invoice_id` → `ticket_id`.

### Telco CRM — Direct Services

Hits each service directly without the gateway. One folder per service:

| Folder | Port |
|---|---|
| Identity Service | :9001 |
| Customer Service | :9002 |
| Product Catalog Service | :9003 |
| Order Service | :9004 |
| Subscription Service | :9005 |
| Usage Service | :9006 |
| Billing Service | :9007 |
| Payment Service | :9008 |
| Notification Service | :9009 |
| Ticket Service | :9010 |

## Collection variables (auto-managed)

| Variable | Populated by |
|---|---|
| `access_token` | Pre-request script (auto-refresh) |
| `customer_id` | Register Customer test script |
| `order_id` | Place Order test script |
| `subscription_id` | List Subscriptions test script |
| `payment_id` | Create Payment test script |
| `invoice_id` | List Invoices test script |
| `ticket_id` | Open Ticket test script |
| `tariff_id` / `tariff_code` | List Tariffs test script (journey 1.3) |

## Notes

- `Idempotency-Key` on order/payment POSTs uses `{{$guid}}` — Postman generates a fresh UUID per
  send so re-sending a request creates a new resource rather than replaying.
- The gateway blocks `/internal/**` — those paths are only available in the Direct collection.
- Admin-only endpoints (create tariff, trigger bill run, assign roles) require a JWT for a Keycloak
  user that has the `ADMIN` or `OPS` realm role.
