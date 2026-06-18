# ADR 008: Saga Orchestration for Order Flow

## Context
The order processing flow spans multiple microservices: Order Service -> Payment Service -> Dispatch Service. Using direct REST calls would create a tightly coupled system and make it difficult to handle failures (requiring rollbacks) at any step.

## Decision
Apply the **Saga Pattern (Orchestration-based)**.
- **Order Service** acts as the Orchestrator.
- When an order is created, its state is `CREATED`.
- Order Service publishes an `OrderCreated` event.
- Payment Service listens, processes the payment, and publishes either a `PaymentCompleted` or `PaymentFailed` event.
- Order Service listens to the payment event. If successful, it proceeds to publish an `OrderPaid` event for the Dispatch Service.
- If any step fails, Order Service will issue Compensating Commands to rollback the preceding steps.

## Consequences
- Order Service becomes more complex as it manages the state machine for the Saga.
- Reduces direct dependencies (promotes loose coupling) between services.
- Safely handles distributed transactions across the system.
