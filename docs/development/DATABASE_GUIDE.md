# Database Guide

## 1. Database Strategy

| Service | Database | Schema/DB Name | Reason |
|---------|----------|---------------|-------|
| User Service | PostgreSQL | `user_db` | Relational data, ACID for auth |
| Restaurant Service | PostgreSQL | `restaurant_db` | JSONB for flexible menu structure |
| Order Service | PostgreSQL | `order_db` | ACID transactions, Outbox table |
| Payment Service | PostgreSQL | `payment_db` | Financial data integrity |
| Dispatch Service | Redis | `db 0` | Geospatial indexing, ephemeral state |

> **Note:** In local/dev environments, run 1 PostgreSQL instance with multiple databases (logical separation). Production can separate into distinct instances if needed.

## 2. Schema Migration (Flyway)

All Java services **MUST** use [Flyway](https://flywaydb.org/) to manage schema changes.

### Migration File Convention

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_addresses_table.sql
├── V3__add_phone_column_to_users.sql
└── V4__create_driver_profiles_table.sql
```

### Naming Rules

```
V{version}__{description}.sql
```

- `V` prefix + version number (sequential)
- Double underscore `__` separator
- Description in `snake_case`
- **NEVER** modify an already applied migration file. Create a new file for changes.

### Flyway Configuration (Spring Boot)

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

## 3. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Table names | snake_case, **plural** | `users`, `orders`, `menu_items` |
| Column names | snake_case | `created_at`, `total_amount`, `is_active` |
| Primary key | `id` (UUID) | `id UUID PRIMARY KEY` |
| Foreign key | `{referenced_table_singular}_id` | `user_id`, `order_id` |
| Boolean columns | `is_` or `has_` prefix | `is_active`, `has_verified` |
| Timestamp columns | `_at` suffix | `created_at`, `updated_at`, `delivered_at` |
| JSONB columns | Descriptive name | `options`, `items`, `operating_hours` |
| Indexes | `idx_{table}_{columns}` | `idx_orders_customer_id` |
| Unique constraints | `uq_{table}_{columns}` | `uq_users_email` |

## 4. PostgreSQL JSONB Usage

Restaurant Service sử dụng JSONB cho flexible menu data:

### When to use JSONB vs Relational columns

| Use JSONB | Use Relational Column |
|------------|----------------------|
| Data structure varies between records (menu options) | Fixed data, queried frequently (name, price) |
| No need to query directly on nested fields | Needs JOIN, GROUP BY, ORDER BY |
| Schema-less / semi-structured data | Needs referential integrity (FK) |

### JSONB Query Examples

```sql
-- Find menu items with the option "Size"
SELECT * FROM menu_items
WHERE options @> '[{"name": "Size"}]';

-- Get restaurants open on Mondays
SELECT * FROM restaurants
WHERE operating_hours->>'mon' IS NOT NULL;
```

### JPA Mapping (Hibernate + vladmihalcea types)

```java
@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id
    private UUID id;

    private String name;
    private BigDecimal price;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<MenuOption> options;  // Auto-serialized to JSONB
}
```

## 5. UUID Strategy

- **All** primary keys use UUID v4 (`gen_random_uuid()` in PostgreSQL)
- Generate UUID at the **database level**, not the application level
- Reason: Avoid collisions in distributed systems, no sequential information leaked

## 6. Soft Delete vs Hard Delete

| Resource | Strategy | Reason |
|----------|----------|-------|
| Users | **Soft delete** (`is_active = false`) | Regulatory, audit trail |
| Orders | **Never delete** | Financial records |
| Payments | **Never delete** | Financial records |
| Menu Items | **Soft delete** (`is_available = false`) | Keep for order history reference |
| Driver Locations | **Hard delete** (Redis TTL) | Ephemeral data |

## 7. Connection Pool Configuration

```yaml
# Spring Boot - HikariCP defaults
spring:
  datasource:
    hikari:
      maximum-pool-size: 10       # Adjust based on pod count
      minimum-idle: 5
      connection-timeout: 30000   # 30s
      idle-timeout: 600000        # 10min
      max-lifetime: 1800000       # 30min
```
