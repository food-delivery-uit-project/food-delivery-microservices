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
 * Kafka Consumer: Listens to delivery-events topic.
 * - DriverAssigned → DRIVER_ASSIGNED (with driverId)
 * - DriverPickedUp → PICKED_UP
 * - OrderDelivered → DELIVERED
 * - DispatchFailed → CANCELLED
 */
@Component
public class DeliveryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventConsumer.class);

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final ObjectMapper objectMapper;

    public DeliveryEventConsumer(UpdateOrderStatusUseCase updateOrderStatusUseCase, ObjectMapper objectMapper) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "delivery-events", groupId = "order-service-group")
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String type = envelope.path("type").asText();
            JsonNode data = envelope.path("data");
            String orderIdStr = data.path("order_id").asText();

            if (orderIdStr.isBlank()) {
                log.warn("delivery-events: missing order_id in message");
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            log.info("Received delivery event type={} orderId={}", type, orderId);

            switch (type) {
                case "DriverAssigned" -> {
                    String driverIdStr = data.path("driver_id").asText();
                    UUID driverId = UUID.fromString(driverIdStr);
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.withDriver(orderId, driverId));
                }
                case "DriverPickedUp" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.PICKED_UP));
                case "OrderDelivered" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.DELIVERED));
                case "DispatchFailed" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.CANCELLED));
                default -> log.debug("Ignoring unhandled delivery event type={}", type);
            }
        } catch (Exception e) {
            log.error("Error processing delivery event: {}", message, e);
        }
    }
}
