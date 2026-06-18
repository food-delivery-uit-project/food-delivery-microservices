package com.fooddelivery.order.adapter.inbound.rest;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderStatus;
import com.fooddelivery.order.domain.port.inbound.CancelOrderUseCase;
import com.fooddelivery.order.domain.port.inbound.CreateOrderUseCase;
import com.fooddelivery.order.domain.port.inbound.GetOrderQuery;
import com.fooddelivery.order.domain.port.inbound.UpdateOrderStatusUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Adapter (Inbound): Handles external order API requests.
 *
 * Header-based auth: Kong forwards X-User-Id and X-User-Role headers.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final GetOrderQuery getOrderQuery;

    public OrderController(
        CreateOrderUseCase createOrderUseCase,
        CancelOrderUseCase cancelOrderUseCase,
        UpdateOrderStatusUseCase updateOrderStatusUseCase,
        GetOrderQuery getOrderQuery
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.getOrderQuery = getOrderQuery;
    }

    /**
     * POST /api/v1/orders — Customer places a new order.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, UUID>>> createOrder(
        @RequestHeader("X-User-Id") UUID customerId,
        @RequestHeader("X-User-Role") String userRole,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        if (!"CUSTOMER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "Only customers can place orders"));
        }

        List<CreateOrderUseCase.OrderItemCommand> items = request.items().stream()
            .map(i -> new CreateOrderUseCase.OrderItemCommand(i.itemId(), i.quantity()))
            .toList();

        CreateOrderUseCase.CreateOrderCommand command = new CreateOrderUseCase.CreateOrderCommand(
            customerId,
            request.restaurantId(),
            items,
            request.deliveryAddress().toDomain(),
            request.specialInstructions()
        );

        UUID orderId = createOrderUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(Map.of("orderId", orderId)));
    }

    /**
     * GET /api/v1/orders/{id} — Get order details.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(
        @PathVariable UUID orderId,
        @RequestHeader("X-User-Id") UUID userId
    ) {
        Order order = getOrderQuery.getById(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderResponseDto.from(order)));
    }

    /**
     * GET /api/v1/orders — Get order history (by customer or restaurant).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponseDto>>> listOrders(
        @RequestHeader("X-User-Id") UUID userId,
        @RequestHeader("X-User-Role") String userRole
    ) {
        List<Order> orders = switch (userRole) {
            case "CUSTOMER" -> getOrderQuery.getByCustomerId(userId);
            case "RESTAURANT_OWNER" -> getOrderQuery.getByRestaurantId(userId);
            default -> List.of();
        };

        List<OrderResponseDto> dtos = orders.stream().map(OrderResponseDto::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    /**
     * PATCH /api/v1/orders/{id}/cancel — Customer cancels an order.
     */
    @PatchMapping("/{orderId}/cancel")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
        @PathVariable UUID orderId,
        @RequestHeader("X-User-Id") UUID userId
    ) {
        cancelOrderUseCase.execute(orderId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * PATCH /api/v1/orders/{id}/accept — Restaurant owner accepts order.
     */
    @PatchMapping("/{orderId}/accept")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> acceptOrder(
        @PathVariable UUID orderId,
        @RequestHeader("X-User-Role") String userRole
    ) {
        if (!"RESTAURANT_OWNER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "Only restaurant owners can accept orders"));
        }
        updateOrderStatusUseCase.execute(
            UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.RESTAURANT_ACCEPTED));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * PATCH /api/v1/orders/{id}/ready — Restaurant marks food ready for pickup.
     */
    @PatchMapping("/{orderId}/ready")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markReady(
        @PathVariable UUID orderId,
        @RequestHeader("X-User-Role") String userRole
    ) {
        if (!"RESTAURANT_OWNER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "Only restaurant owners can mark orders as ready"));
        }
        updateOrderStatusUseCase.execute(
            UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.READY_FOR_PICKUP));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
