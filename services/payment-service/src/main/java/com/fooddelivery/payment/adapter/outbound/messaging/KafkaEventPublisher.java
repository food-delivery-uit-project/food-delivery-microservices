package com.fooddelivery.payment.adapter.outbound.messaging;

import com.fooddelivery.payment.domain.event.PaymentFailedEvent;
import com.fooddelivery.payment.domain.event.PaymentRefundedEvent;
import com.fooddelivery.payment.domain.event.PaymentSuccessEvent;
import com.fooddelivery.payment.domain.port.outbound.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fooddelivery.payment.adapter.outbound.persistence.outbox.OutboxEventJpaEntity;
import com.fooddelivery.payment.adapter.outbound.persistence.outbox.SpringDataOutboxRepository;
import org.springframework.stereotype.Component;
import java.util.Map;

import java.time.Instant;
import java.util.UUID;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private static final String TOPIC = "payment-events";
    private static final String SOURCE = "payment-service";

    private final SpringDataOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(SpringDataOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishSuccess(PaymentSuccessEvent event) {
        send(event.getOrderId().toString(), "PaymentSuccess", new PaymentSuccessData(
                        event.getOrderId().toString(),
                        event.getPaymentId().toString(),
                        event.getAmount().doubleValue(),
                        event.getTransactionId()
                ));
    }

    @Override
    public void publishFailure(PaymentFailedEvent event) {
        send(event.getOrderId().toString(), "PaymentFailed", new PaymentFailedData(
                        event.getOrderId().toString(),
                        event.getPaymentId().toString(),
                        event.getFailureReason()
                ));
    }

    @Override
    public void publishRefunded(PaymentRefundedEvent event) {
        send(event.getOrderId().toString(), "PaymentRefunded", new PaymentRefundedData(
                        event.getOrderId().toString(),
                        event.getPaymentId().toString(),
                        event.getRefundAmount().doubleValue()
                ));
    }

    private void send(String orderId, String eventType, Object data) {
        log.info("Saving event key: {} to outbox for topic: {}", orderId, TOPIC);
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID());
        event.setAggregateType("Order");
        event.setAggregateId(UUID.fromString(orderId));
        event.setEventType(eventType);
        
        Map<String, Object> payloadMap = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        event.setPayload(payloadMap);
        
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);
    }

    // --- CloudEvent Envelope Record ---
    public record CloudEventEnvelope<T>(
            String id,
            String source,
            String type,
            String time,
            String datacontenttype,
            T data
    ) {}

    // --- Inner Records matching Kafka Schema precisely ---
    public record PaymentSuccessData(
            String order_id,
            String payment_id,
            double amount,
            String transaction_id
    ) {}

    public record PaymentFailedData(
            String order_id,
            String payment_id,
            String failure_reason
    ) {}

    public record PaymentRefundedData(
            String order_id,
            String payment_id,
            double refund_amount
    ) {}
}
