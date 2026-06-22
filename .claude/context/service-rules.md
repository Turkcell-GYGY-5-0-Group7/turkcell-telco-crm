# Service Rules

## Structure

- com.telco.{service-name}

---

## API Rules

- /api/v1 mandatory
- `ApiResult<T>` required for all responses

---

## Architecture Enforcement

- One service = one bounded context
- No shared domain models
