# ADR 009: Distributed Tracing with OpenTelemetry

## Context
The microservices architecture consists of 6 services written in multiple languages (Java, Go, Node.js) and communicates via HTTP/REST, gRPC, and Kafka. Tracking errors and request latency across the entire system without a central tracing strategy is extremely difficult.

## Decision
Adopt **OpenTelemetry (OTel)** as the single standard for Distributed Tracing.
- Use W3C Trace Context to propagate `TraceID` and `SpanID` via HTTP Headers, Kafka Headers, and Redis Streams (for the outbox pattern).
- OTel SDK is configured across all 6 services (Spring Boot Starter OTel, Go OTel SDK, Node.js OTel SDK).
- All trace data is exported to the OTel Collector via the OTLP/gRPC protocol.
- OTel Collector pushes the data to Jaeger for visualization.

## Consequences
- Exceptional observability, eliminating blind spots across polyglot microservices.
- Safely maintains context propagation even through complex asynchronous boundaries like Kafka queues and Redis Stream Relays.
- Slightly increases the payload size (headers) on requests and events.
