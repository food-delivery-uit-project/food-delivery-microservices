package com.fooddelivery.order.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.adapter.outbound.persistence.OutboxEventJpaEntity;
import com.fooddelivery.order.adapter.outbound.persistence.SpringDataOutboxRepository;
import com.fooddelivery.order.domain.port.outbound.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound Adapter: Implements EventPublisher via Transactional Outbox Pattern.
 *
 * Instead of publishing directly to Kafka (which may fail after DB commit),
 * we write the event to outbox_events table WITHIN the same transaction.
 * The OutboxEventRelay then asynchronously publishes to Kafka.
 *
 * This guarantees at-least-once delivery and eventual consistency.
 */
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final SpringDataOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(SpringDataOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(String topic, String key, Object event) {
        try {
            // Build CloudEvents envelope
            Map<String, Object> payload = objectMapper.convertValue(event, Map.class);

            OutboxEventJpaEntity outboxEvent = new OutboxEventJpaEntity();
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setAggregateType("Order");
            outboxEvent.setAggregateId(UUID.fromString(key));
            outboxEvent.setEventType(event.getClass().getSimpleName());
            outboxEvent.setPayload(payload);
            outboxEvent.setPublished(false);
            outboxEvent.setCreatedAt(Instant.now());

            outboxRepository.save(outboxEvent);
            log.debug("Saved to outbox: eventType={} aggregateId={}", outboxEvent.getEventType(), key);
        } catch (Exception e) {
            log.error("Failed to write event to outbox: topic={} key={}", topic, key, e);
            throw new RuntimeException("Failed to write event to outbox", e);
        }
    }
}
