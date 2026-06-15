package com.fooddelivery.order.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.domain.model.OrderStatus;
import com.fooddelivery.order.domain.port.inbound.UpdateOrderStatusUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka Consumer: Listens to restaurant-events topic.
 * - OrderAccepted → RESTAURANT_ACCEPTED
 * - OrderRejected → CANCELLED
 * - OrderReadyForPickup → READY_FOR_PICKUP
 */
@Component
public class RestaurantEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventConsumer.class);

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final ObjectMapper objectMapper;

    public RestaurantEventConsumer(UpdateOrderStatusUseCase updateOrderStatusUseCase, ObjectMapper objectMapper) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "restaurant-events", groupId = "order-service-group")
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String type = envelope.path("type").asText();
            JsonNode data = envelope.path("data");
            String orderIdStr = data.path("order_id").asText();

            if (orderIdStr.isBlank()) {
                log.warn("restaurant-events: missing order_id in message");
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            log.info("Received restaurant event type={} orderId={}", type, orderId);

            switch (type) {
                case "OrderAccepted" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.RESTAURANT_ACCEPTED));
                case "OrderRejected" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.CANCELLED));
                case "OrderReadyForPickup" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.READY_FOR_PICKUP));
                default -> log.debug("Ignoring unhandled restaurant event type={}", type);
            }
        } catch (Exception e) {
            log.error("Error processing restaurant event: {}", message, e);
        }
    }
}
