# Order Service

## Purpose (Bounded Context)
Order Service is the central orchestrator of the system. It receives orders from customers, manages the order state, and **acts as the Saga Orchestrator** to coordinate the payment process (Payment Service) and the delivery process (Dispatch Service).

## Directory Structure (Hexagonal Architecture)
This service strictly adheres to **Hexagonal Architecture (Ports & Adapters)** to isolate Core Logic from Infrastructure details.

- `domain/`: Contains core business logic (Entities, Value Objects). Absolutely no dependencies on the Spring Framework, REST, or Kafka.
- `domain/port/inbound`: Interfaces describing Use Cases (e.g., `CreateOrderUseCase`).
- `domain/port/outbound`: Interfaces describing required Infrastructure (e.g., `OrderRepository`, `EventPublisher`).
- `application/`: Implementations of the Use Cases.
- `adapter/inbound/`: Controllers (REST, Kafka Listeners) receiving external requests.
- `adapter/outbound/`: Detailed implementations interacting with DB, Kafka, REST Clients.

## Applied Patterns
- **Saga Orchestration:** Order Service manages the Order State Machine.
- **Transactional Outbox:** Saves data to the DB and publishes an event to the `outbox_events` table simultaneously to guarantee Atomicity. A background outbox worker polls this table and pushes to Kafka.

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8003/swagger-ui/index.html` (Assuming port 8003 for Order Service)
- **OpenAPI JSON:** `http://localhost:8003/v3/api-docs`

## Environment Variables
- `SPRING_DATASOURCE_URL`: (e.g., `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/order_db`)
- `KAFKA_BOOTSTRAP_SERVERS`: (e.g., `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `RESTAURANT_SERVICE_URL`: Internal Base URL of the Restaurant Service.
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry configuration.

## How to Run Locally
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/order_db
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export RESTAURANT_SERVICE_URL=http://localhost:8002

./mvnw spring-boot:run
```
