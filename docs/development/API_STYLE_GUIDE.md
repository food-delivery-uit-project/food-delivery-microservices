# API Style Guide

This document is the **source of truth** for all REST API conventions in the system. All services **MUST** adhere to the following rules.

## 1. URL Conventions

### Base URL Pattern

```
/api/v1/{resource}
```

- Use **plural nouns** for resource names: `/orders`, `/restaurants`, `/users`
- Use **kebab-case** for multi-word resource names: `/menu-items`
- **DO NOT** use verbs in URLs: ❌ `/api/v1/getOrders` ✅ `/api/v1/orders`
- Version in URL path: `/api/v1/...`

### Internal APIs (Service-to-Service)

```
/api/internal/{resource}
```

- Prefix `/api/internal/` for endpoints accessible only within the K8s cluster
- Do not route through Kong Gateway
- Protected by Kubernetes NetworkPolicy

### Nested Resources

```
GET /api/v1/restaurants/{restaurantId}/menu
GET /api/v1/restaurants/{restaurantId}/orders
```

- Maximum **2 levels** of nesting. If deeper is needed → use query parameters.

## 2. HTTP Methods

| Method | Purpose | Idempotent | Request Body |
|--------|---------|------------|-------------|
| GET | Read resource(s) | Yes | No |
| POST | Create resource | No | Yes |
| PUT | Full update (replace) | Yes | Yes |
| PATCH | Partial update | No | Yes |
| DELETE | Remove resource | Yes | No |

### PATCH for State Transitions

```
PATCH /api/v1/orders/{id}/cancel
PATCH /api/v1/orders/{id}/accept
PATCH /api/v1/orders/{id}/ready
PATCH /api/v1/delivery/{orderId}/pickup
PATCH /api/v1/delivery/{orderId}/deliver
```

## 3. Standard Response Format

### Success Response

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-04-22T08:00:00Z"
  }
}
```

### Success Response (List with Pagination)

```json
{
  "success": true,
  "data": [ ... ],
  "meta": {
    "timestamp": "2026-04-22T08:00:00Z",
    "pagination": {
      "page": 1,
      "size": 20,
      "total_items": 156,
      "total_pages": 8
    }
  }
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Order with id 'abc-123' was not found",
    "details": [
      {
        "field": "order_id",
        "message": "No order exists with this ID"
      }
    ]
  },
  "meta": {
    "timestamp": "2026-04-22T08:00:00Z",
    "trace_id": "trace-xyz-789"
  }
}
```

### Error Codes Convention

Format: `{RESOURCE}_{ERROR_TYPE}`

| Code | HTTP Status | Description |
|------|------------|-------------|
| `VALIDATION_ERROR` | 400 | Request body validation failed |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT token |
| `FORBIDDEN` | 403 | Insufficient permissions (wrong role) |
| `ORDER_NOT_FOUND` | 404 | Resource not found |
| `ORDER_INVALID_TRANSITION` | 409 | Invalid state transition |
| `PAYMENT_FAILED` | 422 | Business logic failure |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## 4. Pagination

Use **offset-based pagination** with query parameters:

```
GET /api/v1/orders?page=1&size=20&sort=created_at,desc
```

| Parameter | Default | Max | Description |
|-----------|---------|-----|-------------|
| `page` | 1 | - | Page number (1-indexed) |
| `size` | 20 | 100 | Items per page |
| `sort` | varies | - | Format: `field,direction` (e.g., `created_at,desc`) |

## 5. Filtering

Use query parameters for filtering:

```
GET /api/v1/orders?status=PAID&from_date=2026-04-01&to_date=2026-04-22
GET /api/v1/restaurants?cuisine=VIETNAMESE&lat=10.77&lng=106.70&radius=3
```

## 6. Request Validation

- **Required fields**: Return `400` with specific field errors
- **Validation error response** must include the `details` array with per-field messages

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "message": "must be a valid email address" },
      { "field": "total_amount", "message": "must be greater than 0" }
    ]
  }
}
```

## 7. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| URL path | kebab-case | `/menu-items` |
| JSON field names | **snake_case** | `total_amount`, `created_at` |
| Query parameters | snake_case | `from_date`, `cuisine_type` |
| HTTP headers (custom) | X-Kebab-Case | `X-User-Id`, `X-User-Role` |
| Error codes | UPPER_SNAKE_CASE | `ORDER_NOT_FOUND` |

## 8. Authentication Headers

Kong Gateway injects these headers after JWT validation:

| Header | Source | Description |
|--------|--------|-------------|
| `X-User-Id` | JWT `sub` claim | UUID of authenticated user |
| `X-User-Role` | JWT `role` claim | `CUSTOMER`, `DRIVER`, `RESTAURANT_OWNER`, `ADMIN` |

Services read these headers instead of re-validating JWT.

## 9. OpenAPI / Swagger Documentation (Required)

All REST API endpoints **MUST** be documented using OpenAPI (Swagger).

- **Java Services:** Use `springdoc-openapi-starter-webmvc-ui`.
- **Go Services:** Use `swaggo/swag` and `swaggo/http-swagger`.
- **Node.js Services:** Use `swagger-jsdoc` and `swagger-ui-express`.

Access documentation at:
- Swagger UI: `http://localhost:<port>/swagger-ui/index.html` (Java/Go) or `http://localhost:<port>/api-docs` (Node.js)
- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`

## 10. HTTP Status Codes Usage

| Status | When to use |
|--------|-------------|
| 200 | Successful GET, PUT, PATCH |
| 201 | Successful POST (resource created) |
| 204 | Successful DELETE (no content) |
| 400 | Validation error, malformed request |
| 401 | Missing/invalid authentication |
| 403 | Authenticated but insufficient permissions |
| 404 | Resource not found |
| 409 | Conflict (duplicate, invalid state transition) |
| 422 | Business logic error (payment failed, restaurant closed) |
| 429 | Rate limit exceeded |
| 500 | Internal server error (unexpected) |

## 10. Versioning

- API version in URL: `/api/v1/...`
- Breaking changes require new version: `/api/v2/...`
- Non-breaking additions (new optional fields) do NOT require version bump
