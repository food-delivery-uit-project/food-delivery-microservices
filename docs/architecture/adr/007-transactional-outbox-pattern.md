# ADR 007: Transactional Outbox Pattern for Microservices (Java)

## Context
In an Event-Driven Architecture, services (Order, Payment, User, Restaurant) need to write data to the database (PostgreSQL) and publish events to Kafka. If these two operations are performed independently (Dual-Write), the system is at risk of data inconsistency (e.g., saving to DB succeeds but Kafka crashes).

## Decision
Use the **Transactional Outbox Pattern**.
- All Java services use PostgreSQL.
- Every time a significant state change occurs, the system saves the Domain Entity and an Outbox Event to the `outbox_events` table within the same Database Transaction.
- A background process (Spring `@Scheduled` or Debezium CDC) polls the `outbox_events` table and pushes the messages to Kafka. If successful, the event is marked as published or deleted. This ensures **At-Least-Once Delivery**.

## Consequences
- Increases data integrity. No more data discrepancies between DB and Kafka.
- Increases latency when publishing events due to waiting for the background worker to poll the table.
- Consumers must be designed to be Idempotent to handle duplicate events safely.
