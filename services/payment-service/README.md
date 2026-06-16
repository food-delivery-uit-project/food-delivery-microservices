# Payment Service

## Purpose (Bounded Context)
Payment Service handles financial transactions. It receives payment requests (via Kafka Messages from the Order Service) and interacts with an external payment gateway (mocked).

## Directory Structure (Hexagonal Architecture)
Similar to the Order Service, the Payment Service uses **Hexagonal Architecture**.
- `domain`: Transaction state and business logic.
- `application`: Payment use cases.
- `adapter/inbound`: Consumes `OrderCreated` events from Kafka.
- `adapter/outbound`: Writes to the DB, publishes events (via Outbox), and calls the mocked Payment Gateway.

## Applied Patterns
- **Saga Participant:** Payment Service does not orchestrate; it merely responds to events from the Orchestrator. It listens for payment commands and returns either a `PaymentCompleted` or `PaymentFailed` result.
- **Transactional Outbox:** Similar to Order Service, it ensures atomic database writes and event publishing.

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8004/swagger-ui/index.html` (Assuming port 8004 for Payment Service)
- **OpenAPI JSON:** `http://localhost:8004/v3/api-docs`

## Environment Variables
- `SPRING_DATASOURCE_URL`: (e.g., `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/payment_db`)
- `KAFKA_BOOTSTRAP_SERVERS`: (e.g., `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry configuration.

## How to Run Locally
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/payment_db
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

./mvnw spring-boot:run
```
