package com.fooddelivery.restaurant.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox Event Relay: Polls outbox_events table and publishes to Kafka.
 *
 * Pattern: Transactional Outbox
 * - Polls every ${outbox.polling-interval-ms} milliseconds
 * - For each unpublished event, publishes to appropriate Kafka topic
 * - Marks event as published after successful Kafka publish
 *
 * Topic mapping is derived from event type.
 */
@Component
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);

    private final SpringDataOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventRelay(
        SpringDataOutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.polling-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEventJpaEntity> unpublished = outboxRepository.findUnpublished();
        if (unpublished.isEmpty()) return;

        log.debug("Relaying {} outbox events to Kafka", unpublished.size());

        for (OutboxEventJpaEntity event : unpublished) {
            try {
                String topic = resolveTopicForEventType(event.getEventType());
                String cloudEventMessage = buildCloudEventMessage(event);

                kafkaTemplate.send(topic, event.getAggregateId().toString(), cloudEventMessage).get();
                outboxRepository.markAsPublished(event.getId());

                log.info("Published outbox event eventType={} aggregateId={} topic={}",
                    event.getEventType(), event.getAggregateId(), topic);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} eventType={}",
                    event.getId(), event.getEventType(), e);
            }
        }
    }

    private String buildCloudEventMessage(OutboxEventJpaEntity event) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("source", "restaurant-service");
        envelope.put("type", event.getEventType());
        envelope.put("time", Instant.now().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("data", event.getPayload());
        return objectMapper.writeValueAsString(envelope);
    }

    private String resolveTopicForEventType(String eventType) {
        return "restaurant-events";
    }
}
