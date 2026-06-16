# Dispatch Service

## Purpose (Bounded Context)
Dispatch Service is written in Golang and is responsible for managing the online status and locations of drivers, as well as Matching algorithms to pair an order with the closest available driver.

## Technology & Architecture
- **Language:** Go 1.22
- **Primary Database:** Redis (uses Geospatial Indexing `GEOADD`, `GEORADIUS` to search for drivers within a radius).
- **Source Code Architecture:** Idiomatic Go (Basic Clean Architecture).
  - `cmd/`: Contains the `main.go` file.
  - `internal/domain/`: Entities and core logic.
  - `internal/matching/`: Driver matching algorithms.
  - `internal/repository/`: Redis operations.
  - `internal/service/`: Application orchestration logic.
  - `internal/kafka/`: Kafka consumers and producers.

## Applied Patterns
- **Geospatial Queries:** Uses Redis to optimize distance-based search speed.
- **Redis Stream Outbox:** Solves the Dual-Write problem in a NoSQL environment by using Lua Scripts to update the State and create an Event (`XADD`) within a single Atomic Transaction. A Goroutine acts as a Relay, reading the stream and publishing to Kafka safely.

## OpenAPI / Swagger Documentation
API documentation is automatically generated. When the service is running, you can view the Swagger UI at:
- **Swagger UI:** `http://localhost:8005/swagger/index.html` (Assuming port 8005 for Dispatch Service)
- **OpenAPI JSON:** `http://localhost:8005/swagger/doc.json`

## Environment Variables
- `REDIS_URL`: Connection string to Redis (e.g., `food-delivery-redis-master.databases.svc.cluster.local:6379`)
- `KAFKA_BROKERS`: Kafka broker list (e.g., `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OpenTelemetry configuration.

## How to Run Locally
```bash
export REDIS_URL=localhost:6379
export KAFKA_BROKERS=localhost:9092

go run cmd/server/main.go
```
