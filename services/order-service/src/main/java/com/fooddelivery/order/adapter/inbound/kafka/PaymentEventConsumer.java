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
 * Kafka Consumer: Listens to payment-events topic.
 * - PaymentSuccess → PAID
 * - PaymentFailed → CANCELLED
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(UpdateOrderStatusUseCase updateOrderStatusUseCase, ObjectMapper objectMapper) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String type = envelope.path("type").asText();
            JsonNode data = envelope.path("data");
            String orderIdStr = data.path("order_id").asText();

            if (orderIdStr.isBlank()) {
                log.warn("payment-events: missing order_id in message: {}", message);
                return;
            }

            UUID orderId = UUID.fromString(orderIdStr);
            log.info("Received payment event type={} orderId={}", type, orderId);

            switch (type) {
                case "PaymentSuccess" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.PAID));
                case "PaymentFailed" ->
                    updateOrderStatusUseCase.execute(
                        UpdateOrderStatusUseCase.UpdateStatusCommand.of(orderId, OrderStatus.CANCELLED));
                default -> log.debug("Ignoring unhandled payment event type={}", type);
            }
        } catch (Exception e) {
            log.error("Error processing payment event: {}", message, e);
        }
    }
}
