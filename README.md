# Distributed CDC Engine

Custom CDC engine from scratch to stream real-time database mutations.

Built without Debezium - uses low-level Logical Decoding (pgoutput).

**Why this engine?**

- Bypasses heavy runtime polling, uses PostgreSQL WAL directly for sub-50ms lag
- Fault-tolerant resume via LSN checkpointing (at-least-once delivery)

**How it works:**

1. Listens to `pgoutput` logical replication slot
2. Translates WAL (INSERT/UPDATE/DELETE) -> typed JSON event
3. Tracks LSN for crash recovery

**Stack:** Java 11, PostgreSQL, Kafka (coming), Docker (coming)

**Status:** Project bootstrap - BUILD SUCCESS
