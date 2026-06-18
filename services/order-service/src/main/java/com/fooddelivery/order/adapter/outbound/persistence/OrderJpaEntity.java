package com.fooddelivery.order.adapter.outbound.persistence;

import com.fooddelivery.order.domain.model.OrderStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity for Order persistence.
 * Deliberately separated from the domain model Order to keep domain pure.
 * Mapped by OrderMapper.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Type(JsonType.class)
    @Column(name = "delivery_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> deliveryAddress;

    @Type(JsonType.class)
    @Column(name = "items", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> items;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "estimated_delivery_time")
    private Instant estimatedDeliveryTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID restaurantId) { this.restaurantId = restaurantId; }

    public Map<String, Object> getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(Map<String, Object> deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID driverId) { this.driverId = driverId; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public Instant getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(Instant estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
