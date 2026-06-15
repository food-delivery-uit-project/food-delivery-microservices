# ADR 007: Transactional Outbox Pattern cho các Microservices (Java)

## Context
Trong kiến trúc Event-Driven, các service (Order, Payment, User, Restaurant) cần thực hiện ghi dữ liệu vào CSDL (PostgreSQL) và phát hành sự kiện (publish event) vào Kafka. Nếu thực hiện 2 thao tác này độc lập (Dual-Write), hệ thống có nguy cơ mất đồng bộ dữ liệu (VD: lưu DB thành công nhưng Kafka sập).

## Decision
Sử dụng **Transactional Outbox Pattern**.
- Tất cả các service Java sử dụng PostgreSQL.
- Mỗi lần có thay đổi trạng thái quan trọng, hệ thống sẽ lưu Domain Entity và một Outbox Event vào bảng `outbox_events` trong cùng một Database Transaction.
- Một tiến trình chạy ngầm (Spring `@Scheduled` hoặc Debezium CDC) sẽ đọc bảng `outbox_events` và đẩy thông báo sang Kafka. Nếu đẩy thành công, đánh dấu/xoá event. Đảm bảo **At-Least-Once Delivery**.

## Consequences
- Tăng tính toàn vẹn dữ liệu. Không còn tình trạng sai lệch giữa DB và Kafka.
- Tăng độ trễ (latency) khi phát event do phải chờ background worker quét bảng.
- Consumer phải thiết kế theo chuẩn Idempotent để xử lý duplicate events.
