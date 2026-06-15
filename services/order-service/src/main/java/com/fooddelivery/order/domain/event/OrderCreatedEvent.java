package com.fooddelivery.order.domain.event;

import com.fooddelivery.order.domain.model.DeliveryAddress;
import com.fooddelivery.order.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Published when a new order is created.
 * CloudEvents-compatible envelope is applied by the Kafka publisher adapter.
 */
public record OrderCreatedEvent(
    UUID orderId,
    UUID customerId,
    UUID restaurantId,
    BigDecimal totalAmount,
    String paymentMethod,
    List<OrderItem> items,
    DeliveryAddress deliveryAddress
) {}
