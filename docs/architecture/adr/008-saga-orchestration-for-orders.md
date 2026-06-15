# ADR 008: Saga Orchestration cho Order Flow

## Context
Quá trình xử lý đơn hàng trải dài qua nhiều microservices: Order Service -> Payment Service -> Dispatch Service. Việc dùng REST call trực tiếp sẽ tạo ra tightly coupled system và khó xử lý khi một bước thất bại (cần rollback).

## Decision
Áp dụng **Saga Pattern (Orchestration-based)**.
- **Order Service** đóng vai trò là Orchestrator.
- Khi tạo đơn hàng, Order ở trạng thái `CREATED`.
- Order Service phát hành event `OrderCreated`.
- Payment Service lắng nghe, xử lý thanh toán và phát event `PaymentCompleted` hoặc `PaymentFailed`.
- Order Service lắng nghe event từ Payment, nếu thành công thì tiếp tục phát `OrderPaid` để Dispatch Service xử lý.
- Nếu một bước thất bại, Order Service sẽ gửi các Command bồi hoàn (Compensating Commands) để rollback các bước trước.

## Consequences
- Order Service phức tạp hơn vì chứa state machine cho Saga.
- Giảm thiểu sự phụ thuộc trực tiếp (loose coupling) giữa các service.
- Xử lý distributed transaction an toàn.
