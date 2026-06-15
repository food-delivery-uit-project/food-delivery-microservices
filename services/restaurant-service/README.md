# Restaurant Service

## Mục Đích (Bounded Context)
Restaurant Service chịu trách nhiệm quản lý Menu, Thông tin nhà hàng (Giờ mở cửa, Địa chỉ), và trạng thái hoạt động của nhà hàng. Đây là dữ liệu gốc (Source of Truth) cho các món ăn và giá cả trước khi Order Service tiến hành đặt hàng.

## Cấu trúc Thư mục (Layered Architecture)
Service này được xây dựng theo kiến trúc **Layered Architecture**.

- `controller`: Chứa các REST API cho phía khách hàng và các Internal API (nội bộ).
- `service`: Chứa logic thao tác với dữ liệu nhà hàng.
- `repository`: Truy cập cơ sở dữ liệu.
- `model`: Thực thể (Entities).

## API Nội Bộ (Internal API)
Service này expose một số endpoint nội bộ ở dạng `/api/internal/...` để các service khác (như Order Service) lấy thông tin giá cả và menu mà không qua Authentication của Gateway.

## Biến Môi Trường (Environment Variables)
- `SPRING_DATASOURCE_URL`: (vd: `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/restaurant_db`)
- `SPRING_DATASOURCE_USERNAME`: User DB
- `SPRING_DATASOURCE_PASSWORD`: Password DB
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Cấu hình OpenTelemetry.

## Cách chạy Local
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/restaurant_db
export SPRING_DATASOURCE_USERNAME=food_user
export SPRING_DATASOURCE_PASSWORD=food_password

./mvnw spring-boot:run
```
