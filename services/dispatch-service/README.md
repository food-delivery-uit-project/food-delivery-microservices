# Dispatch Service

## Mục Đích (Bounded Context)
Dispatch Service được viết bằng Golang, chịu trách nhiệm quản lý vị trí trực tuyến của các tài xế và ghép cuốc (Matching) giữa đơn hàng và tài xế gần nhất. 

## Công nghệ & Kiến trúc
- **Ngôn ngữ:** Go 1.22
- **Cơ sở dữ liệu chính:** Redis (sử dụng Geospatial Index `GEOADD`, `GEORADIUS` để tìm kiếm tài xế trong bán kính).
- **Kiến trúc mã nguồn:** Idiomatic Go (Clean Architecture cơ bản).
  - `cmd/`: Chứa file `main.go`.
  - `internal/domain/`: Entities và logic cốt lõi.
  - `internal/matching/`: Thuật toán ghép cuốc xe.
  - `internal/repository/`: Thao tác với Redis.
  - `internal/service/`: Application logic và điều phối.
  - `internal/kafka/`: Kafka consumer và producer.

## Patterns được áp dụng
- **Geospatial Queries:** Dùng Redis để tối ưu tốc độ tìm kiếm khoảng cách.
- **Redis Stream Outbox:** Khắc phục lỗi Dual-Write trong môi trường NoSQL bằng cách sử dụng Lua Scripts để cập nhật State và tạo Event (`XADD`) trong cùng một Atomic Transaction. Một Goroutine sẽ làm Relay đọc stream và publish sang Kafka.

## Biến Môi Trường (Environment Variables)
- `REDIS_URL`: (vd: `redis:6379`)
- `KAFKA_BROKERS`: (vd: `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Endpoint xuất OpenTelemetry.

## Cách chạy Local
```bash
export REDIS_URL=localhost:6379
export KAFKA_BROKERS=localhost:9092

go run cmd/server/main.go
```
