package com.fooddelivery.order.adapter.outbound.persistence;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.port.outbound.OrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound Adapter: Implements the OrderRepository port using JPA.
 * This is the bridge between domain port and Spring Data JPA.
 */
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository springDataOrderRepository;

    public JpaOrderRepository(SpringDataOrderRepository springDataOrderRepository) {
        this.springDataOrderRepository = springDataOrderRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = OrderMapper.toJpaEntity(order);
        OrderJpaEntity saved = springDataOrderRepository.save(entity);
        return OrderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return springDataOrderRepository.findById(id)
            .map(OrderMapper::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(UUID customerId) {
        return springDataOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .stream()
            .map(OrderMapper::toDomain)
            .toList();
    }

    @Override
    public List<Order> findByRestaurantId(UUID restaurantId) {
        return springDataOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
            .stream()
            .map(OrderMapper::toDomain)
            .toList();
    }
}
