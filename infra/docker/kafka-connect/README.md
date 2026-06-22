# Kafka Connect (Debezium)

Kafka Connect runs the Debezium PostgreSQL connector that reads each service's transactional
outbox table and routes events to Kafka (ADR-005, ADR-009). It is part of the core profile and
starts with `make up`. PostgreSQL already runs with `wal_level=logical` so CDC works out of the box.

Connect REST API: http://localhost:8083

## Registering connectors

One outbox connector is registered per service (each service owns its database, ADR-006).
Connector definitions live in `connectors/`. Files ending in `.example.json` are templates and are
skipped by the registration script.

```bash
cd infra/docker/kafka-connect/connectors
cp outbox-connector.example.json customer-outbox-connector.json
# edit: name, database.dbname, slot.name, publication.name

cd ../../..          # back to infra/
make register-connectors
```

`register-connectors.sh` waits for Connect to be ready and POSTs every non-example `*.json` in
`connectors/` (idempotent: updates config if the connector already exists).

## Column mapping (platform outbox -> Debezium EventRouter)

The example maps the `outbox_event` columns created by `starter-outbox` (migration V900):

| Outbox column | EventRouter role |
| --- | --- |
| `id` | event id (Kafka message id) |
| `aggregate_id` | message key |
| `event_type` | event type header |
| `payload` | message value (JSON) |
| `aggregate_type` | routing field -> topic `<aggregate_type>.events` |

## Prerequisites per service

A connector can only be created after the service's `outbox_event` table exists (the service has run
its Flyway migrations, including the platform `db/migration/platform` location). Bring up the
service first, then register its connector.

## Notes

- Converters default to JSON with schemas disabled (see `compose.yml`). To use Avro with the
  Schema Registry instead, switch the connector's `value.converter` to the Avro converter and point
  it at `http://schema-registry:8081`.
- Inspect status: `curl -s localhost:8083/connectors | jq` and
  `curl -s localhost:8083/connectors/<name>/status | jq`.
