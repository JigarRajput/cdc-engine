# Distributed CDC Engine

Custom CDC engine built from scratch to stream real-time database mutations.

Built without Debezium — uses low-level Logical Decoding (`pgoutput`).

**Current Status:** 🟢 LIVE — INSERT capture working

```log
BEGIN
Insert CDC EVENT: CdcEvent{op=INSERT, schema='public', table='users', ... lsn=25115944}
COMMIT
```

## What works

- [x] Replication connection (`my_slot` + `my_pub`)
- [x] INSERT capture, WAL parsing & CdcEvent generation
- [x] LSN tracking
- [ ] UPDATE/DELETE parsing
- [ ] Kafka producer
- [ ] Checkpointing

## Run

```powershell
mvn compile exec:java -D"exec.mainClass=com.jigar.engine.CdcEngine"
```

## Test

```sql
INSERT INTO users(id, name) VALUES (100, 'test');
```
