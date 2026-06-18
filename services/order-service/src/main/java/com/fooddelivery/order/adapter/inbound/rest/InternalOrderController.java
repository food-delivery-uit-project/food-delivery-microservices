package com.fooddelivery.order.adapter.inbound.rest;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.port.inbound.GetOrderQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal REST Controller — accessible only within the K8s cluster.
 * Used by Dispatch Service to fetch order details.
 *
 * NOTE: Not routed through Kong. Protected by K8s NetworkPolicy.
 */
@RestController
@RequestMapping("/api/internal/orders")
public class InternalOrderController {

    private final GetOrderQuery getOrderQuery;

    public InternalOrderController(GetOrderQuery getOrderQuery) {
        this.getOrderQuery = getOrderQuery;
    }

    /**
     * GET /api/internal/orders/{id} — Called by Dispatch Service.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(@PathVariable UUID orderId) {
        Order order = getOrderQuery.getById(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderResponseDto.from(order)));
    }
}
