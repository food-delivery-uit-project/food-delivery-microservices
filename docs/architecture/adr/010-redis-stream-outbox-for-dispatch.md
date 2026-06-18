# ADR 010: Using Redis Streams as Outbox for Dispatch Service

## Context
Dispatch Service (Golang) uses Redis as its primary database to provide high-speed Geospatial Matching (`GEOADD`, `GEORADIUS`). However, Redis does not support multi-table ACID Transactions like PostgreSQL, leading to a Dual-Write problem when attempting to update driver statuses and publish events to Kafka simultaneously.

## Decision
Adopt the **Lua Script + Redis Streams** architecture.
- Status updates (Key-Value/Hash) and event creation (`XADD`) into a Redis Stream are encapsulated within a single Lua Script. Redis executes the Lua Script as an Atomic Transaction.
- A Background Worker (Go Routine) acts as a Relay, consuming data from the Stream (using Consumer Groups) and publishing it to Kafka safely.
- After successfully publishing, the Relay executes an `XACK` command to acknowledge the message.

## Consequences
- Completely resolves the Dual-Write issue within the Dispatch Service.
- Preserves the high performance and low latency characteristics of Redis.
- Increases the complexity of the Dispatch Service slightly (requires managing Redis Streams and Consumer Groups).
