package com.fooddelivery.order.application;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.port.inbound.GetOrderQuery;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;

import java.util.List;
import java.util.UUID;

/**
 * Application Service: Get Order Query implementation.
 * <p>
 * This service handles all read operations (queries) for orders,
 * adhering to the CQRS pattern at a logical level.
 */
public class GetOrderQueryService implements GetOrderQuery {

    private final OrderRepository orderRepository;

    public GetOrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Retrieves an order by its unique identifier.
     *
     * @param orderId the UUID of the order
     * @return the Order entity
     * @throws ResourceNotFoundException if no order is found with the given ID
     */
    @Override
    public Order getById(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("ORDER_NOT_FOUND",
                "Order not found: " + orderId));
    }

    /**
     * Retrieves all orders associated with a specific customer.
     *
     * @param customerId the UUID of the customer
     * @return a list of Order entities, or an empty list if none found
     */
    @Override
    public List<Order> getByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Retrieves all orders associated with a specific restaurant.
     *
     * @param restaurantId the UUID of the restaurant
     * @return a list of Order entities, or an empty list if none found
     */
    @Override
    public List<Order> getByRestaurantId(UUID restaurantId) {
        return orderRepository.findByRestaurantId(restaurantId);
    }
}
