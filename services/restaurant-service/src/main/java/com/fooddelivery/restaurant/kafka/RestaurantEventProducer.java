package com.fooddelivery.restaurant.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fooddelivery.restaurant.outbox.OutboxEventJpaEntity;
import com.fooddelivery.restaurant.outbox.SpringDataOutboxRepository;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RestaurantEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventProducer.class);
    private final SpringDataOutboxRepository outboxRepository;

    public RestaurantEventProducer(SpringDataOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void publishOrderAccepted(UUID orderId, UUID restaurantId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("order_id", orderId.toString());
        data.put("restaurant_id", restaurantId.toString());
        data.put("status", "ACCEPTED");

        OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(orderId);
        outboxEvent.setEventType("OrderAccepted");
        outboxEvent.setPayload(data);
        outboxEvent.setCreatedAt(java.time.Instant.now());
        outboxRepository.save(outboxEvent);
    }

    public void publishOrderReadyForPickup(UUID orderId, UUID restaurantId, Double lat, Double lng) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("order_id", orderId.toString());
        data.put("restaurant_id", restaurantId.toString());
        data.put("restaurant_lat", lat);
        data.put("restaurant_lng", lng);
        data.put("status", "READY_FOR_PICKUP");

        OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(orderId);
        outboxEvent.setEventType("OrderReadyForPickup");
        outboxEvent.setPayload(data);
        outboxEvent.setCreatedAt(java.time.Instant.now());
        outboxRepository.save(outboxEvent);
    }

    public void publishRestaurantStatusChanged(UUID restaurantId, boolean isActive) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("restaurant_id", restaurantId.toString());
        data.put("is_active", isActive);

        OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateType("Restaurant");
        outboxEvent.setAggregateId(restaurantId);
        outboxEvent.setEventType("RestaurantStatusChanged");
        outboxEvent.setPayload(data);
        outboxEvent.setCreatedAt(java.time.Instant.now());
        outboxRepository.save(outboxEvent);
    }
}
