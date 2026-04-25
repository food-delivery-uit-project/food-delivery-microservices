# Testing Strategy

## 1. Testing Pyramid

```
        ╱  E2E  ╲           ← Ít nhất, chạy chậm nhất
       ╱---------╲
      ╱ Integration╲        ← Trung bình
     ╱--------------╲
    ╱   Unit Tests    ╲     ← Nhiều nhất, chạy nhanh nhất
   ╱-------------------╲
```

| Level           | Target                                               | Tools                                               | Coverage Target                             |
| --------------- | ---------------------------------------------------- | --------------------------------------------------- | ------------------------------------------- |
| **Unit**        | Domain logic, business rules, algorithms             | JUnit 5 + Mockito (Java), go test (Go), Jest (Node) | **≥ 80%** cho domain/service layers         |
| **Integration** | DB queries, Kafka produce/consume, REST client calls | Testcontainers (real DB/Kafka in Docker)            | Tất cả repository methods + Kafka consumers |
| **E2E**         | Full order flow qua nhiều services                   | Python script + HTTP calls                          | Happy path + 2 failure paths                |

## 2. Unit Testing

### Java - Hexagonal Services (Order, Payment)

Domain layer test **KHÔNG cần Spring Context** — chạy < 1 giây:

```java
// domain/service/OrderDomainServiceTest.java
class OrderDomainServiceTest {
    @Test
    void shouldRejectInvalidStatusTransition() {
        Order order = Order.create(customerId, restaurantId, items, address);
        // Order starts as CREATED
        assertThrows(InvalidTransitionException.class,
            () -> order.transitionTo(OrderStatus.DELIVERED));
    }

    @Test
    void shouldCalculateDeliveryFee() {
        Money fee = OrderDomainService.calculateDeliveryFee(2.5); // 2.5km
        assertEquals(Money.of(15000, "VND"), fee);
    }
}
```

Application layer test với **mocked ports**:

```java
// application/CreateOrderApplicationServiceTest.java
@ExtendWith(MockitoExtension.class)
class CreateOrderApplicationServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock EventPublisher eventPublisher;
    @Mock RestaurantClient restaurantClient;
    @InjectMocks CreateOrderApplicationService service;

    @Test
    void shouldCreateOrderAndPublishEvent() {
        when(restaurantClient.validateItems(any(), any()))
            .thenReturn(validatedItems);

        OrderId result = service.createOrder(command);

        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(any(OrderCreatedEvent.class));
    }
}
```

### Java - Layered Services (User, Restaurant)

```java
// service/UserServiceTest.java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class,
            () -> userService.register(registerRequest));
    }
}
```

### Go (Dispatch Service)

Matching algorithm test — pure Go, no Redis:

```go
// internal/matching/matcher_test.go
func TestFindNearestDriver(t *testing.T) {
    drivers := []domain.Driver{
        {ID: "d1", Lat: 10.770, Lng: 106.700, Status: domain.Available},
        {ID: "d2", Lat: 10.780, Lng: 106.710, Status: domain.Available},
        {ID: "d3", Lat: 10.790, Lng: 106.720, Status: domain.Busy},
    }
    center := domain.Location{Lat: 10.771, Lng: 106.701}

    result := FindNearestAvailable(center, drivers, 3.0)

    assert.Equal(t, "d1", result.ID) // d1 is closest and available
}
```

### Node.js (Notification Service)

```typescript
// kafka/handlers/order-events.test.ts
describe("OrderEventHandler", () => {
    it("should send SSE when OrderCreated received", async () => {
        const mockSSEManager = { send: jest.fn() };
        const handler = new OrderEventHandler(mockSSEManager);

        await handler.handle({
            type: "OrderCreated",
            data: { order_id: "ord-123", customer_id: "usr-456" },
        });

        expect(mockSSEManager.send).toHaveBeenCalledWith(
            "ord-123",
            expect.objectContaining({ status: "CREATED" })
        );
    });
});
```

## 3. Integration Testing (Testcontainers)

Mỗi service dùng Testcontainers để spin up real DB/Kafka trong Docker:

Lưu ý quan trọng khi chạy local:

-   Testcontainers yêu cầu Docker daemon đang chạy.
-   Một số test Spring có thể in Kafka Admin warning/error log trong giai đoạn bootstrap topic.
-   Kết luận pass/fail phải dựa vào tổng kết Maven/JUnit cuối cùng (`Failures: 0, Errors: 0`), không dựa vào một dòng warning lẻ.

### Java

```java
@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrder() {
        // Tests actual PostgreSQL queries
    }
}
```

### Kafka Integration Test

```java
@SpringBootTest
@Testcontainers
@EmbeddedKafka
class PaymentEventConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Test
    void shouldProcessOrderCreatedEvent() {
        // Publish event to embedded Kafka
        // Verify Payment record created in DB
    }
}
```

## 4. Running Tests

```bash
# Single service
make test svc=order-service

# All services
make test-all

# With coverage (Java)
cd services/order-service && ./mvnw test jacoco:report

# With coverage (Go)
cd services/dispatch-service && go test -coverprofile=coverage.out ./...

# With coverage (Node)
cd services/notification-service && npm run test:coverage
```

## 5. CI Pipeline Test Requirements

| Check                        | Threshold   | Action on Fail                   |
| ---------------------------- | ----------- | -------------------------------- |
| Unit Tests                   | All pass    | Block merge                      |
| Integration Tests            | All pass    | Block merge                      |
| Code Coverage (domain layer) | ≥ 80%       | Warning (not blocking initially) |
| Lint                         | Zero errors | Block merge                      |
