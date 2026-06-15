# Payment Service

## Mục Đích (Bounded Context)
Payment Service chịu trách nhiệm xử lý các giao dịch thanh toán. Nó nhận yêu cầu thanh toán (thông qua Kafka Message từ Order Service) và tương tác với cổng thanh toán (mock).

## Cấu trúc Thư mục (Hexagonal Architecture)
Tương tự Order Service, Payment Service sử dụng **Hexagonal Architecture**.
- `domain`: Trạng thái và logic giao dịch.
- `application`: Use cases thanh toán.
- `adapter/inbound`: Nhận sự kiện `OrderCreated` từ Kafka.
- `adapter/outbound`: Ghi vào DB, xuất event (Outbox), gọi tới Payment Gateway giả lập.

## Patterns được áp dụng
- **Saga Participant:** Payment Service không điều phối, mà chỉ phản hồi sự kiện từ Orchestrator. Nó lắng nghe lệnh thanh toán và trả về kết quả `PaymentCompleted` hoặc `PaymentFailed`.
- **Transactional Outbox:** Tương tự Order Service, lưu DB và event nguyên tử.

## Biến Môi Trường (Environment Variables)
- `SPRING_DATASOURCE_URL`: (vd: `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/payment_db`)
- `KAFKA_BOOTSTRAP_SERVERS`: (vd: `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Cấu hình OpenTelemetry.

## Cách chạy Local
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/payment_db
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

./mvnw spring-boot:run
```
