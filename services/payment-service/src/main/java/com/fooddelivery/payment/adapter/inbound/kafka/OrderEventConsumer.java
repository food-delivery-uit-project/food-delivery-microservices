package com.fooddelivery.payment.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.payment.domain.model.PaymentMethod;
import com.fooddelivery.payment.domain.port.inbound.ProcessPaymentUseCase;
import com.fooddelivery.payment.domain.port.outbound.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ProcessPaymentUseCase processPaymentUseCase,
                              PaymentRepository paymentRepository,
                              ObjectMapper objectMapper) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "payment-service-group")
    @Transactional
    public void consume(String message) {
        log.info("Received Kafka event on topic order-events: {}", message);
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String id = envelope.path("id").asText();
            String type = envelope.path("type").asText();

            if (id == null || id.isBlank()) {
                log.warn("Received event without id. Message ignored.");
                return;
            }

            // Check event idempotency
            if (paymentRepository.isEventProcessed(id)) {
                log.info("Event with id {} has already been processed. Skipping.", id);
                return;
            }

            // We only process OrderCreatedEvent
            if ("OrderCreatedEvent".equals(type)) {
                JsonNode data = envelope.path("data");
                UUID orderId = UUID.fromString(data.path("order_id").asText());
                UUID customerId = UUID.fromString(data.path("customer_id").asText());
                BigDecimal totalAmount = new BigDecimal(data.path("total_amount").asText());
                String paymentMethodStr = data.path("payment_method").asText();
                PaymentMethod method = PaymentMethod.valueOf(paymentMethodStr);

                log.info("Triggering ProcessPaymentUseCase for order: {}, customer: {}, amount: {}", orderId, customerId, totalAmount);
                processPaymentUseCase.processPayment(orderId, customerId, totalAmount, method);

                // Save event processing record
                paymentRepository.markEventProcessed(id, type);
                log.info("Event {} processed successfully.", id);
            } else {
                log.debug("Event type {} is ignored by OrderEventConsumer.", type);
            }

        } catch (Exception ex) {
            log.error("Error processing order-events message: {}", message, ex);
            // Throw exception to trigger Kafka retry/DLQ if configured, or catch it depending on policy.
            // Since we want standard behavior, we throw it.
            throw new RuntimeException("Error processing Kafka event", ex);
        }
    }
}
