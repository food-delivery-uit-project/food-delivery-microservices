# ADR 010: Sử dụng Redis Stream làm Outbox cho Dispatch Service

## Context
Dispatch Service (Golang) sử dụng Redis làm DB chính để phục vụ tính năng Geospatial Matching tốc độ cao (`GEOADD`, `GEORADIUS`). Tuy nhiên, Redis không hỗ trợ cơ chế Transaction ACID với nhiều bảng như PostgreSQL, dẫn đến lỗi Dual-Write khi muốn cập nhật trạng thái tài xế và publish event sang Kafka.

## Decision
Áp dụng kiến trúc **Lua Script + Redis Streams**.
- Cập nhật trạng thái (Key-Value/Hash) và tạo event (`XADD`) vào Redis Stream được gói gọn trong một Lua Script. Redis thực thi Lua Script dưới dạng Atomic Transaction.
- Một Background Worker (Go Routine) đóng vai trò Relay, đọc dữ liệu từ Stream (sử dụng Consumer Groups) và publish tới Kafka.
- Sau khi publish thành công, Relay gọi lệnh `XACK` để xác nhận.

## Consequences
- Giải quyết dứt điểm lỗi Dual-Write trong Dispatch Service.
- Giữ nguyên được hiệu năng cao của Redis.
- Tăng độ phức tạp của Dispatch Service (cần quản lý Redis Stream và Consumer Group).
