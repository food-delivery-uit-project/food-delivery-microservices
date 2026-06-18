# User Service

## Purpose (Bounded Context)
User Service manages all Customer information including Profiles, Addresses, and basic Authentication. This is the entry point into the Food Delivery system when users log in or create an account.

## Directory Structure (Layered Architecture)
This service is built using a simple **Layered Architecture**, which is suitable for modules that are mostly CRUD-oriented rather than having complex business logic.

- `controller`: Contains the REST API Endpoints.
- `service`: Contains the Business Logic.
- `repository`: Communicates with the Database via Spring Data JPA.
- `model`/`dto`: Data Models and Data Transfer Objects.

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8001/swagger-ui/index.html` (Assuming port 8001 for User Service)
- **OpenAPI JSON:** `http://localhost:8001/v3/api-docs`

## Environment Variables
- `SPRING_DATASOURCE_URL`: Connection string to PostgreSQL (e.g., `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/user_db`)
- `SPRING_DATASOURCE_USERNAME`: Database user
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry Traces export endpoint (e.g., `http://otel-collector.observability.svc:4317`)

## How to Run Locally (Without Docker/K8s)
```bash
# 1. Ensure PostgreSQL is running on port 5432
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/user_db
export SPRING_DATASOURCE_USERNAME=food_user
export SPRING_DATASOURCE_PASSWORD=food_password

# 2. Run with Maven
./mvnw spring-boot:run
```
