package com.fooddelivery.order.domain.port.outbound;

import com.fooddelivery.order.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: Repository interface for Order persistence.
 * Implemented by JPA adapter.
 *
 * NOTE: This is NOT a Spring Data JPA interface.
 * It is a pure Java interface that the adapter implements.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    java.util.List<Order> findByCustomerId(UUID customerId);

    java.util.List<Order> findByRestaurantId(UUID restaurantId);
}
