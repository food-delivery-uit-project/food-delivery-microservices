# Notification Service

## Purpose (Bounded Context)
Notification Service is written in Node.js (TypeScript). This service is responsible for listening to Events from Kafka (such as `OrderCreated`, `OrderPaid`, `DriverAssigned`, `DriverPickedUp`) and pushing Real-time notifications to the customer's Client using **Server-Sent Events (SSE)**.

## Technology & Architecture
- **Language:** Node.js 20, TypeScript.
- **Framework:** Express.js for the API and SSE connections.
- **Messaging:** `kafkajs` for the Kafka Consumer.
- **Directory Structure:**
  - `src/kafka/`: Processes Kafka events.
  - `src/sse/`: Manages the list of active SSE Clients.
  - `src/controllers/`: HTTP Controllers.

## Applied Patterns
- **Event-Driven UI (Server-Sent Events):** SSE is a perfect, lightweight alternative to WebSockets for applications that only require a unidirectional data flow from Server -> Client (such as pushing order status updates).

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8006/api-docs` (Assuming port 8006 for Notification Service)
- **OpenAPI JSON:** `http://localhost:8006/api-docs-json`

## Environment Variables
- `PORT`: HTTP server port (default 8080).
- `KAFKA_BROKERS`: (e.g., `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry configuration.

## How to Run Locally
```bash
export KAFKA_BROKERS=localhost:9092
export PORT=8006

npm ci
npm run build
npm start
```
