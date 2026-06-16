# Restaurant Service

## Purpose (Bounded Context)
Restaurant Service is responsible for managing Menus, Restaurant Information (Operating Hours, Addresses), and the operational status of restaurants. This acts as the Source of Truth for menu items and prices before the Order Service processes an order.

## Directory Structure (Layered Architecture)
This service is built using a **Layered Architecture**.

- `controller`: Contains the REST APIs for external clients and Internal APIs for service-to-service communication.
- `service`: Contains logic for manipulating restaurant data.
- `repository`: Handles database access.
- `model`: Entities.

## Internal API
This service exposes certain internal endpoints under `/api/internal/...` allowing other services (like the Order Service) to fetch pricing and menu information without passing through Gateway Authentication.

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8002/swagger-ui/index.html` (Assuming port 8002 for Restaurant Service)
- **OpenAPI JSON:** `http://localhost:8002/v3/api-docs`

## Environment Variables
- `SPRING_DATASOURCE_URL`: (e.g., `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/restaurant_db`)
- `SPRING_DATASOURCE_USERNAME`: Database user
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry configuration.

## How to Run Locally
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/restaurant_db
export SPRING_DATASOURCE_USERNAME=food_user
export SPRING_DATASOURCE_PASSWORD=food_password

./mvnw spring-boot:run
```
