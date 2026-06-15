package com.fooddelivery.payment.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.payment.domain.port.inbound.RefundPaymentUseCase;
import com.fooddelivery.payment.domain.port.outbound.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CompensationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CompensationEventConsumer.class);

    private final RefundPaymentUseCase refundPaymentUseCase;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public CompensationEventConsumer(RefundPaymentUseCase refundPaymentUseCase,
                                     PaymentRepository paymentRepository,
                                     ObjectMapper objectMapper) {
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"order-events"}, groupId = "payment-service-compensation-group")
    @Transactional
    public void consume(String message) {
        log.info("Received Kafka event on compensation topics: {}", message);
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String id = envelope.path("id").asText();
            String type = envelope.path("type").asText();

            if (id == null || id.isBlank()) {
                log.warn("Received compensation event without id. Message ignored.");
                return;
            }

            // Check event idempotency
            if (paymentRepository.isEventProcessed(id)) {
                log.info("Compensation event with id {} has already been processed. Skipping.", id);
                return;
            }

            // Handle OrderCancelled from Saga Orchestrator
            if ("OrderCancelledEvent".equals(type) || "OrderCancelled".equals(type)) {
                JsonNode data = envelope.path("data");
                UUID orderId = UUID.fromString(data.path("orderId").asText());
                String reason = data.path("reason").asText("Saga compensation rollback");

                log.info("Received compensation event {} for order {}, type: {}. Triggering refund.", id, orderId, type);
                refundPaymentUseCase.refundPaymentByOrderId(orderId, reason);

                // Save event processing record
                paymentRepository.markEventProcessed(id, type);
                log.info("Compensation event {} processed successfully.", id);
            } else {
                log.debug("Compensation event type {} is ignored.", type);
            }

        } catch (Exception ex) {
            log.error("Error processing compensation message: {}", message, ex);
            throw new RuntimeException("Error processing Kafka compensation event", ex);
        }
    }
}
