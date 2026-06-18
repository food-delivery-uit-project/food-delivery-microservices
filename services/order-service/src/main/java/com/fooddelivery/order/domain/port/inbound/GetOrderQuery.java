package com.fooddelivery.order.domain.port.inbound;

import com.fooddelivery.order.domain.model.Order;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port: Use case for querying an order.
 */
public interface GetOrderQuery {

    Order getById(UUID orderId);

    List<Order> getByCustomerId(UUID customerId);

    List<Order> getByRestaurantId(UUID restaurantId);
}
