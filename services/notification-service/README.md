# Notification Service

## Mục Đích (Bounded Context)
Notification Service được viết bằng Node.js (TypeScript). Service này có nhiệm vụ lắng nghe các Event từ Kafka (như `OrderCreated`, `OrderPaid`, `DriverAssigned`, `DriverPickedUp`) và đẩy thông báo Real-time (Thời gian thực) về cho Client của khách hàng thông qua công nghệ **Server-Sent Events (SSE)**.

## Công nghệ & Kiến trúc
- **Ngôn ngữ:** Node.js 20, TypeScript.
- **Framework:** Express.js cho API và SSE connections.
- **Messaging:** `kafkajs` để làm Consumer.
- **Cấu trúc thư mục:**
  - `src/kafka/`: Xử lý Kafka events.
  - `src/sse/`: Quản lý danh sách các SSE Clients đang kết nối.
  - `src/controllers/`: HTTP Controllers.

## Patterns được áp dụng
- **Event-Driven UI (Server-Sent Events):** SSE là lựa chọn hoàn hảo và nhẹ nhàng hơn WebSocket đối với ứng dụng chỉ cần luồng dữ liệu 1 chiều từ Server -> Client (như cập nhật trạng thái đơn hàng).

## Biến Môi Trường (Environment Variables)
- `PORT`: Cổng chạy HTTP server (mặc định 8080).
- `KAFKA_BROKERS`: (vd: `food-delivery-kafka-kafka-bootstrap.kafka.svc:9092`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Endpoint OpenTelemetry.

## Cách chạy Local
```bash
export KAFKA_BROKERS=localhost:9092
export PORT=8006

npm ci
npm run build
npm start
```
