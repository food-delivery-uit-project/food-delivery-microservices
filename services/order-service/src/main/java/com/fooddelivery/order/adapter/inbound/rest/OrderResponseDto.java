package com.fooddelivery.order.adapter.inbound.rest;

import com.fooddelivery.order.domain.model.DeliveryAddress;
import com.fooddelivery.order.domain.model.Money;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Order resource.
 */
public record OrderResponseDto(
    UUID id,
    UUID customerId,
    UUID restaurantId,
    List<OrderItemDto> items,
    DeliveryAddressDto deliveryAddress,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal totalAmount,
    OrderStatus status,
    UUID driverId,
    String specialInstructions,
    Instant createdAt,
    Instant updatedAt
) {
    public record OrderItemDto(UUID itemId, String name, int quantity, BigDecimal unitPrice, BigDecimal total) {}
    public record DeliveryAddressDto(String addressLine, double lat, double lng) {}

    public static OrderResponseDto from(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
            .map(i -> new OrderItemDto(
                i.itemId(), i.name(), i.quantity(),
                i.unitPrice().amount(),
                i.total().amount()
            ))
            .toList();

        DeliveryAddress addr = order.getDeliveryAddress();
        return new OrderResponseDto(
            order.getId(),
            order.getCustomerId(),
            order.getRestaurantId(),
            itemDtos,
            new DeliveryAddressDto(addr.addressLine(), addr.lat(), addr.lng()),
            order.getSubtotal().amount(),
            order.getDeliveryFee().amount(),
            order.getTotalAmount().amount(),
            order.getStatus(),
            order.getDriverId(),
            order.getSpecialInstructions(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
