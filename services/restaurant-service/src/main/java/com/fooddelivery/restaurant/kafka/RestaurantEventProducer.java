package com.fooddelivery.restaurant.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RestaurantEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RestaurantEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderAccepted(UUID orderId, UUID restaurantId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("order_id", orderId.toString());
        data.put("restaurant_id", restaurantId.toString());
        data.put("status", "ACCEPTED");

        CloudEvent<Map<String, Object>> event = new CloudEvent<>(
                eventId,
                "/services/restaurant-service",
                "OrderAccepted",
                data
        );

        log.info("Publishing OrderAccepted event: {}", eventId);
        kafkaTemplate.send("restaurant-events", orderId.toString(), event);
    }

    public void publishOrderReadyForPickup(UUID orderId, UUID restaurantId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("order_id", orderId.toString());
        data.put("restaurant_id", restaurantId.toString());
        data.put("status", "READY_FOR_PICKUP");

        CloudEvent<Map<String, Object>> event = new CloudEvent<>(
                eventId,
                "/services/restaurant-service",
                "OrderReadyForPickup",
                data
        );

        log.info("Publishing OrderReadyForPickup event: {}", eventId);
        kafkaTemplate.send("restaurant-events", orderId.toString(), event);
    }

    public void publishRestaurantStatusChanged(UUID restaurantId, boolean isActive) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("restaurant_id", restaurantId.toString());
        data.put("is_active", isActive);

        CloudEvent<Map<String, Object>> event = new CloudEvent<>(
                eventId,
                "/services/restaurant-service",
                "RestaurantStatusChanged",
                data
        );

        log.info("Publishing RestaurantStatusChanged event: {}", eventId);
        kafkaTemplate.send("restaurant-events", restaurantId.toString(), event);
    }
}
