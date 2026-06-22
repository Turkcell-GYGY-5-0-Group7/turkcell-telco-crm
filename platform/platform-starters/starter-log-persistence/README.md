# starter-log-persistence

Optional JDBC persistence of request and exception logs, for local and test environments.

Production logging follows ADR-012 (structured JSON to Loki) and ADR-015 (traceId/correlationId in
`ApiResult.meta`). This starter is an additive debugging aid that is disabled by default. When
enabled, handled exceptions are stored in `exception_logs` and the `logId` returned in error
responses (`ApiError.logId`) resolves to a row you can inspect.

## What it provides

- `JdbcExceptionLogWriter` (implements the platform `ExceptionLogWriter` port) writing `exception_logs`.
- `JdbcRequestLogWriter` (implements the mediator `RequestLogWriter` port) writing `request_logs`.
- Optional `HttpRequestLogFilter` that records one row per HTTP request for non-mediator services.

`starter-api` automatically picks up any `ExceptionLogWriter` bean and stamps `logId` onto error
responses; no extra wiring is needed.

## Usage (local/test only)

Add the dependency (version managed by `platform-bom`) and include the platform migrations:

```xml
<dependency>
  <groupId>com.telco.platform</groupId>
  <artifactId>starter-log-persistence</artifactId>
</dependency>
```

```properties
spring.flyway.locations=classpath:db/migration,classpath:db/migration/platform
telco.platform.logging.persistence.enabled=true
# optional, for non-mediator (simple-service-layer) services:
telco.platform.logging.persistence.http-enabled=true
```

Recommended: enable only in `local` and `test` Spring profiles.

## Configuration (`telco.platform.logging.persistence.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | Master switch for DB log persistence. |
| `request-table` | `request_logs` | Request-log table name. |
| `exception-table` | `exception_logs` | Exception-log table name. |
| `http-enabled` | `false` | Also persist one row per HTTP request (non-mediator services). |
