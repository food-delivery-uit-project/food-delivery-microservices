# Order Service

## Mục Đích (Bounded Context)
Order Service là thành phần trung tâm của hệ thống. Nó chịu trách nhiệm nhận đơn hàng từ khách hàng, lưu trữ trạng thái đơn hàng và **đóng vai trò là Saga Orchestrator** để điều phối quy trình thanh toán (Payment Service) và giao hàng (Dispatch Service).

## Cấu trúc Thư mục (Hexagonal Architecture)
Service này tuân thủ nghiêm ngặt **Kiến trúc Lục giác (Ports & Adapters)** để tách biệt Core Logic khỏi Infrastructure.

- `domain/`: Chứa nghiệp vụ lõi (Entities, Value Objects). Tuyệt đối không phụ thuộc vào Spring Framework, REST, hay Kafka.
- `domain/port/inbound`: Các interface mô tả Use Case (e.g. `CreateOrderUseCase`).
- `domain/port/outbound`: Các interface mô tả Infrastructure cần thiết (e.g. `OrderRepository`, `EventPublisher`).
- `application/`: Cài đặt các Use Case.
- `adapter/inbound/`: Các Controller (REST, Kafka Listener) nhận request từ bên ngoài vào.
- `adapter/outbound/`: Cài đặt chi tiết thao tác với DB, Kafka, REST Client.

## Patterns được áp dụng
- **Saga Orchestration:** Order Service quản lý State Machine của đơn hàng.
- **Transactional Outbox:** Lưu DB và phát hành event vào bảng `outbox_events` để đảm bảo tính nguyên tử (Atomicity). Outbox worker sẽ quét và đẩy vào Kafka.

## Biến Môi Trường (Environment Variables)
- `SPRING_DATASOURCE_URL`: (vd: `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/order_db`)
- `KAFKA_BOOTSTRAP_SERVERS`: (vd: `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `RESTAURANT_SERVICE_URL`: Base URL nội bộ của Restaurant Service.
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Cấu hình OpenTelemetry.

## Cách chạy Local
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/order_db
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export RESTAURANT_SERVICE_URL=http://localhost:8002

./mvnw spring-boot:run
```
