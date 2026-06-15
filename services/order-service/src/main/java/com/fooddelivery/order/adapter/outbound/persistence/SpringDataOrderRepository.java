package com.fooddelivery.order.adapter.outbound.persistence;

import com.fooddelivery.order.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Order persistence.
 * ONLY used by JpaOrderRepository adapter — not exposed to domain.
 */
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<OrderJpaEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);
}
