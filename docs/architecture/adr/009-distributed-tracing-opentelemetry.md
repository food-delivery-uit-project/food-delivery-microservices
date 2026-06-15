# ADR 009: Distributed Tracing với OpenTelemetry

## Context
Hệ thống microservices gồm 6 services viết bằng nhiều ngôn ngữ khác nhau (Java, Go, Node.js) và giao tiếp qua HTTP/REST, gRPC, và Kafka. Việc theo dõi lỗi và độ trễ (latency) của một request xuyên suốt hệ thống rất khó khăn.

## Decision
Áp dụng **OpenTelemetry (OTel)** làm chuẩn duy nhất cho Distributed Tracing.
- Sử dụng W3C Trace Context để truyền TraceID và SpanID qua HTTP Headers và Kafka Headers.
- OTel SDK được cấu hình trên tất cả 6 services (Spring Boot Starter OTel, Go OTel SDK, Node.js OTel SDK).
- Toàn bộ trace data được export về OTel Collector qua giao thức OTLP/gRPC.
- OTel Collector đẩy dữ liệu về Jaeger để trực quan hóa.

## Consequences
- Khả năng Observability vượt trội.
- Tăng đôi chút payload size (headers) trên các request.
