package com.fooddelivery.user.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutboxEvent persistence.
 * ONLY used by OutboxEventRelay and KafkaEventPublisher.
 */
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findUnpublished();

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.published = true WHERE e.id = :id")
    void markAsPublished(UUID id);
}
