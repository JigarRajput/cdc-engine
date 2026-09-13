package com.jigar.engine;

import org.postgresql.replication.PGReplicationStream;

import com.jigar.model.CdcEvent;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class CdcEngine {

    private final PGReplicationStream stream;
    private final RelationCache cache;

    public CdcEngine(PGReplicationStream stream, RelationCache cache) {
        this.stream = stream;
        this.cache = cache;
    }

    public void start() throws Exception {
        
        while(true) {
            ByteBuffer buffer = stream.readPending();

            if (buffer == null) {
                TimeUnit.MILLISECONDS.sleep(100);
                continue;
            }

            long lsn = stream.getLastReceiveLSN().asLong(); // grab it now, tied to this message
            
            int type = buffer.get(); // first byte = message type

            switch ((char)type) {
                case 'R': // RELATION
                    WalParser.parseRelation(buffer, cache);
                    break;
                case 'B': // BEGIN
                    System.out.println("BEGIN");
                    break;
                case 'I': // INSERT
                    CdcEvent insertEvent = WalParser.parseInsert(buffer, lsn, cache);  
                    
                    System.out.println("Insert CDC EVENT: "+ insertEvent.toString());
                    break;
                case 'C': // COMMIT
                    System.out.println("COMMIT");
                    break;
            }

            stream.setAppliedLSN(stream.getLastReceiveLSN());
            stream.setFlushedLSN(stream.getLastReceiveLSN());
        }
    }

    public static void main(String[] args) throws Exception {
        
        PGReplicationStream stream = new ReplicationConnection().connect();
        RelationCache cache = new RelationCache();

        new CdcEngine(stream, cache).start();
    }
}