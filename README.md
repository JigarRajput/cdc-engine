# Distributed CDC Engine

Custom CDC engine built from scratch to stream real-time database mutations.

Built without Debezium — uses low-level Logical Decoding (`pgoutput`).

**Current Status:** 🟢 LIVE — INSERT capture working

```log
BEGIN
Insert CDC EVENT: CdcEvent{op=INSERT, schema='public', table='users', ... lsn=25115944}
COMMIT
```

## 🛠️ Feature Roadmap & Progress

* ✅ **Replication Connection** (`my_slot` + `my_pub`)
* ✅ **INSERT Capture** (WAL parsing & `CdcEvent` generation)
* ✅ **LSN Tracking** (Log Sequence Number stream offsets)
* ⏳ **UPDATE/DELETE Parsing** (Raw byte stream layout expansion)
* ⏳ **Kafka Producer Integration** (Decoupled event pipeline)
* ⏳ **Fault-Tolerant Checkpointing** (State recovery mechanism)

## 🚀 Execution & Verification

### Run the Engine
```powershell
mvn compile exec:java -D"exec.mainClass=com.jigar.engine.CdcEngine"
```

### Test Target Mutation
```sql
INSERT INTO users(id, name) VALUES (100, 'test');
```
