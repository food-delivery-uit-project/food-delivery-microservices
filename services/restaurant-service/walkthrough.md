# Hướng dẫn Kiểm tra & Xác minh Hệ thống (User Service & Restaurant Service)

Hệ thống hiện tại đã hoàn thiện đầy đủ **User Service** và **Restaurant Service** với các đặc tả logic nghiệp vụ nghiêm ngặt, cơ chế bảo mật xác thực header qua Gateway Kong, ánh xạ kiểu dữ liệu nâng cao (JSONB, Array), và giao tiếp bất đồng bộ qua Kafka.

---

## 1. Restaurant Service - Tổng quan Kiến trúc & Điểm Nổi bật

Dịch vụ **Restaurant Service** đã được cấu hình chạy trên cổng **8082** nội bộ và map ra cổng **8002** trên máy Host.

### Ánh xạ Kiểu Dữ liệu Nâng cao (Hypersistence Utils)
- **Mảng Cuisine (`cuisine_types`)**: Được ánh xạ bằng `@Type(StringArrayType.class)` trên trường `String[]` kết hợp với `columnDefinition = "_varchar"` để Hibernate schema validator kiểm duyệt chính xác cấu trúc mảng PostgreSQL.
- **Thời gian hoạt động (`operating_hours`)**: Được ánh xạ kiểu JSONB sang `Map<String, DayHours>` trong `Restaurant.java`.
- **Tùy chọn món ăn (`options`)**: Được ánh xạ kiểu JSONB sang `List<MenuItemOption>` trong `MenuItem.java`.

### Logic Tính Tiền Nội Bộ (validate-items)
Endpoint nội bộ `POST /api/internal/restaurants/{id}/validate-items` thực hiện tính toán giá trị đơn hàng thực tế như sau:
1. Xác minh món ăn (`item_id`) có tồn tại, thuộc về nhà hàng (`{id}`) và đang ở trạng thái còn hàng (`is_available = true`).
2. Với mỗi món ăn, duyệt qua danh sách `selected_options` do khách hàng chọn.
3. Tìm kiếm option tương ứng trong DB và lấy ra `price_modifier` (giá cộng thêm) của choice tương ứng, cộng dồn vào giá gốc của món ăn.
4. Tính toán tổng tiền `subtotal` bằng cách nhân đơn giá sau tùy chọn với số lượng `quantity`.

### Kafka Integration (CloudEvents)
- **PaymentEventConsumer**: Lắng nghe topic `payment-events`. Khi nhận được event `PaymentSuccess`, hệ thống tự động log thông tin chuẩn bị món ăn.
- **RestaurantEventProducer**: Khi chủ quán thực hiện các hành động:
  - Cập nhật trạng thái nhà hàng (`/status`).
  - Xác nhận đơn (`/orders/{orderId}/accept`).
  - Báo đơn hàng sẵn sàng (`/orders/{orderId}/ready`).
  Hệ thống sẽ publish các event tương ứng (`RestaurantStatusChanged`, `OrderAccepted`, `OrderReadyForPickup`) dưới định dạng **CloudEvents v1.0** chuẩn lên topic `restaurant-events`.

---

## 2. API Endpoints của Restaurant Service (Local Host: 8002 / Kong Gateway: 8000)

### API Công khai (Public APIs)

#### Tìm kiếm & Phân trang Nhà hàng
- **Method / URL**: `GET http://localhost:8002/api/v1/restaurants`
- **Query Params**:
  - `cuisine`: Lọc theo món ăn (ví dụ: `Vietnamese`, `Japanese`) -> Sử dụng Postgres Native Query `ANY(cuisine_types)`.
  - `search`: Tìm kiếm theo tên nhà hàng.
  - `page` / `size`: Phân trang dữ liệu.

#### Xem Chi tiết Nhà hàng
- **Method / URL**: `GET http://localhost:8002/api/v1/restaurants/{id}`

#### Chủ quán Tạo Nhà hàng mới (Yêu cầu Role `RESTAURANT_OWNER`)
- **Method / URL**: `POST http://localhost:8002/api/v1/restaurants`
- **Headers bắt buộc**:
  - `X-User-Id`: `<UUID của chủ quán>`
  - `X-User-Role`: `RESTAURANT_OWNER`
- **Body (JSON)**:
```json
{
  "name": "Bánh Mì Ngon",
  "description": "Bánh mì Việt Nam truyền thống",
  "address_line": "123 Nguyễn Trãi, Quận 5, TP.HCM",
  "lat": 10.762622,
  "lng": 106.660172,
  "cuisine_types": ["Vietnamese", "StreetFood"],
  "operating_hours": {
    "monday": { "open": "06:00", "close": "22:00" },
    "tuesday": { "open": "06:00", "close": "22:00" }
  },
  "image_url": "https://example.com/banhmi.png"
}
```

#### Cập nhật Trạng thái hoạt động (Open/Close)
- **Method / URL**: `PATCH http://localhost:8002/api/v1/restaurants/{id}/status`
- **Headers bắt buộc**: `X-User-Id` và `X-User-Role: RESTAURANT_OWNER`
- **Body (JSON)**:
```json
{
  "is_active": false
}
```

#### Xem thực đơn quán (Menu)
- **Method / URL**: `GET http://localhost:8002/api/v1/restaurants/{id}/menu`

#### Thay thế toàn bộ thực đơn
- **Method / URL**: `PUT http://localhost:8002/api/v1/restaurants/{id}/menu`
- **Headers bắt buộc**: `X-User-Id` và `X-User-Role: RESTAURANT_OWNER`
- **Body (JSON)**:
```json
{
  "categories": [
    {
      "name": "Món Chính",
      "sort_order": 1,
      "items": [
        {
          "name": "Bánh Mì Đặc Biệt",
          "description": "Đầy đủ chả, pate, xá xíu",
          "price": 35000.00,
          "image_url": "https://example.com/banhmi-db.png",
          "is_available": true,
          "options": [
            {
              "name": "Kích cỡ",
              "required": true,
              "choices": [
                { "name": "Thường", "price_modifier": 0 },
                { "name": "Lớn", "price_modifier": 10000.00 }
              ]
            },
            {
              "name": "Topping thêm",
              "required": false,
              "choices": [
                { "name": "Thêm Pate", "price_modifier": 5000.00 },
                { "name": "Thêm Chả", "price_modifier": 7000.00 }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

#### Chủ quán xác nhận đơn hàng
- **Method / URL**: `POST http://localhost:8002/api/v1/restaurants/{id}/orders/{orderId}/accept`
- **Headers bắt buộc**: `X-User-Id` và `X-User-Role: RESTAURANT_OWNER`

#### Chủ quán báo đơn hàng đã làm xong (Sẵn sàng nhận)
- **Method / URL**: `POST http://localhost:8002/api/v1/restaurants/{id}/orders/{orderId}/ready`
- **Headers bắt buộc**: `X-User-Id` và `X-User-Role: RESTAURANT_OWNER`

---

### API Nội bộ (Internal APIs)

#### Xác thực & Tính toán tiền đơn hàng từ Order Service
- **Method / URL**: `POST http://localhost:8002/api/internal/restaurants/{restaurantId}/validate-items`
- **Bảo mật**: Open (Bỏ qua JWT và không cần các X-Headers).
- **Body (JSON)**:
```json
{
  "items": [
    {
      "item_id": "8a3d132a-d603-4903-888f-51a82f3ef008",
      "quantity": 2,
      "selected_options": [
        { "option_name": "Kích cỡ", "choice_label": "Lớn" },
        { "option_name": "Topping thêm", "choice_label": "Thêm Pate" }
      ]
    }
  ]
}
```
- **Phản hồi**:
```json
{
  "success": true,
  "data": {
    "valid": true,
    "subtotal": 100000.00,
    "items": [
      {
        "item_id": "8a3d132a-d603-4903-888f-51a82f3ef008",
        "name": "Bánh Mì Đặc Biệt",
        "price": 35000.00,
        "option_price": 15000.00,
        "subtotal": 100000.00,
        "available": true,
        "message": "Success"
      }
    ]
  },
  "meta": {
    "timestamp": "2026-06-12T09:20:00Z"
  }
}
```

---

## 3. Xác minh Swagger UI (SpringDoc OpenAPI 3)
Cấu hình Swagger của Restaurant Service đã được định cấu hình trỏ Server URL mặc định về **Gateway Kong** (`http://localhost:8000`). 

Bạn có thể truy cập tài liệu API tự động tại:
- **Swagger UI**: [http://localhost:8002/swagger-ui/index.html](http://localhost:8002/swagger-ui/index.html)
- **OpenAPI Specs JSON**: [http://localhost:8002/v3/api-docs](http://localhost:8002/v3/api-docs)
- Để chạy test các API bảo mật, nhấn nút **Authorize** ở góc trên Swagger UI, điền ID người dùng vào mục `X-User-Id` và điền `RESTAURANT_OWNER` vào mục `X-User-Role`.
