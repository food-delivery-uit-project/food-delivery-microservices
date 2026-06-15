# User Service

## Mục Đích (Bounded Context)
User Service quản lý toàn bộ thông tin về khách hàng (Customer) bao gồm hồ sơ (Profile), địa chỉ (Addresses), và xác thực cơ bản. Đây là điểm khởi đầu trong hệ thống Food Delivery khi người dùng tiến hành đăng nhập và tạo hồ sơ.

## Cấu trúc Thư mục (Layered Architecture)
Service này được xây dựng theo kiến trúc **Layered Architecture** đơn giản, phù hợp với các module mang tính chất CRUD nhiều hơn là logic nghiệp vụ phức tạp.

- `controller`: Chứa các REST API Endpoints.
- `service`: Chứa Business Logic (xử lý nghiệp vụ).
- `repository`: Giao tiếp với Database qua Spring Data JPA.
- `model`/`dto`: Các thực thể Data Models và Data Transfer Objects.

## Biến Môi Trường (Environment Variables)
- `SPRING_DATASOURCE_URL`: Chuỗi kết nối đến PostgreSQL (vd: `jdbc:postgresql://postgres.databases.svc.cluster.local:5432/user_db`)
- `SPRING_DATASOURCE_USERNAME`: User kết nối DB
- `SPRING_DATASOURCE_PASSWORD`: Password kết nối DB
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Endpoint xuất OpenTelemetry Traces (vd: `http://otel-collector.observability.svc:4317`)

## Cách chạy Local (Without Docker/K8s)
```bash
# 1. Đảm bảo bạn đã có PostgreSQL chạy ở cổng 5432
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/user_db
export SPRING_DATASOURCE_USERNAME=food_user
export SPRING_DATASOURCE_PASSWORD=food_password

# 2. Khởi chạy với Maven
./mvnw spring-boot:run
```
