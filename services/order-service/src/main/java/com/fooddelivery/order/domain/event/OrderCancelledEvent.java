package com.fooddelivery.order.domain.event;

import java.util.UUID;

/**
 * Domain Event: Published when an order is cancelled.
 */
public record OrderCancelledEvent(
    UUID orderId,
    UUID customerId,
    UUID restaurantId,
    String reason,
    String cancelledBy
) {}
