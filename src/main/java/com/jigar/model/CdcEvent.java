package com.jigar.model;

import java.time.Instant;
import java.util.Map;

public class CdcEvent {
    public Operation op;           // INSERT/UPDATE/DELETE
    public String schema;          // public
    public String table;           // users
    public Map<String, Object> before;
    public Map<String, Object> after;
    public long lsn;
    public Instant timestamp;
    public long transactionId;

    public CdcEvent(Operation op, String schema, String table, 
                    Map<String, Object> before, Map<String, Object> after, 
                    long lsn) {
        this.op = op;
        this.schema = schema;
        this.table = table;
        this.before = before;
        this.after = after;
        this.lsn = lsn;
        this.timestamp = Instant.now();
    }

    @Override
    public String toString() {
        return "CdcEvent{" +
                "op=" + op +
                ", schema='" + schema + '\'' +
                ", table='" + table + '\'' +
                ", before=" + before +
                ", after=" + after +
                ", lsn=" + lsn +
                ", timestamp=" + timestamp +
                ", transactionId=" + transactionId +
                '}';
    }
    
}