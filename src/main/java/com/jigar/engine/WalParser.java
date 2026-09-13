package com.jigar.engine;

import java.util.Map;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.jigar.model.CdcEvent;
import com.jigar.model.Operation;
import com.jigar.model.Relation;

public class WalParser {

     /**
     * Parses a pgoutput "Relation" (R) message and caches the relation's metadata.
     * <p>
     * WHY THIS MESSAGE MATTERS:
     * Postgres does NOT send schema/table name or column names with every row
     * change (INSERT/UPDATE/DELETE) — that would be wasteful. Instead, it sends
     * ONE Relation message per relation (per replication session, or whenever the
     * table's structure changes), and every subsequent row-change message only
     * carries the numeric relationId. This method decodes that one-time metadata
     * message and stores it so later INSERT/UPDATE/DELETE parsing can look up
     * "relationId 16384 = public.users, columns [id, email, created_at]".
     * <p>
     * WIRE FORMAT (everything after the leading 'R' type byte, already consumed
     * by the caller):
     *   Int32   relation OID
     *   String  schema name        (null-terminated)
     *   String  table name         (null-terminated)
     *   Int8    replica identity
     *   Int16   column count (N)
     *   repeated N times:
     *     Int8    column flags
     *     String  column name      (null-terminated)
     *     Int32   column type OID
     *     Int32   column type modifier
     * <p>
     * IMPORTANT: every field above MUST be read, in order, even if we don't use
     * the value. ByteBuffer's read position only moves forward — skipping a field
     * (not calling get()/getInt() for it) leaves the cursor pointing at the wrong
     * byte for everything that follows, corrupting all subsequent parsing until
     * the next stream restart.
     */
    public static void parseRelation(ByteBuffer buffer, RelationCache cache) {

        // Postgres's internal numeric ID for this table (from pg_class.oid).
        // Row-change messages (INSERT/UPDATE/DELETE) only carry this ID, not the
        // schema/table name directly — this is the key we cache against.
        int relationId = buffer.getInt();

        // Null-terminated strings, e.g. "public\0" and "users\0".
        String schema = readString(buffer);
        String table = readString(buffer);

        // REPLICA IDENTITY — controls how much "before" data UPDATE/DELETE
        // messages will contain later:
        //   'd' (default) -> only primary key columns appear in the "before" image
        //   'n' (nothing)  -> no "before" image at all
        //   'f' (full)     -> the ENTIRE old row appears in the "before" image
        //   'i' (index)    -> columns of a specific unique index appear
        // We're only consuming this byte for now (must read it to stay aligned).
        // Worth caching later once UPDATE/DELETE parsing needs to know how much
        // "before" data to expect.
        byte replicaIdentity = buffer.get();

        // How many columns this table has — drives the loop below.
        short nCols = buffer.getShort();

        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < nCols; i++) {

            // Bitmask; the only currently-defined bit (value 1) means "this
            // column is part of the replica identity key" (e.g. primary key).
            // Not used yet — read to stay aligned, can be tracked per-column
            // TODO: later if we need to know which columns are key columns.
            byte flags = buffer.get();

            // Actual column name, e.g. "id", "email".
            String colName = readString(buffer);

            // Postgres's pg_type OID for this column's data type
            // (e.g. 23 = int4, 25 = text, 1114 = timestamp). Needs a lookup
            // TODO: table if we ever want the human-readable type name.
            int typeOid = buffer.getInt();

            // Extra type precision/length info, e.g. the 50 in varchar(50).
            // Most types that don't need this send -1.
            int typeMod = buffer.getInt();

            colNames.add(colName);
        }

        // Cache this relation's metadata so INSERT/UPDATE/DELETE parsing can
        // resolve relationId -> schema/table/column names.
        Relation relation = new Relation(relationId, schema, table, colNames);
        cache.put(relation);
    }

    /**
     * Parses a pgoutput "Insert" (I) message into a CdcEvent.
     * <p>
     * Wire format (after the leading 'I' byte, already consumed by the caller):
     *   Int32   relation OID
     *   Int8    tuple type — always 'N' for inserts
     *   Int16   column count (N)
     *   repeated N times, in the same order as the cached Relation's columns:
     *     Int8    column format: 'n' = NULL, 't' = text value follows, 'u' = TOAST unchanged
     *     [if 't']  Int32 length + that many value bytes
     * <p>
     * Relies on a prior 'R' (Relation) message being cached — the wire message
     * only has relationId + raw values, no column names, so we map by position
     * using rel.columns.
     * <p>
     * 'u' should never appear in an INSERT — it means "value unchanged since
     * last time," which only makes sense for UPDATE (a brand-new row has no
     * prior value to be unchanged from). Seeing it here, or any other
     * unrecognized type byte, almost certainly means the buffer desynced
     * upstream — so both cases throw loudly instead of silently corrupting
     * the rest of the row.
     */
    public static CdcEvent parseInsert(ByteBuffer buffer, long lsn, RelationCache cache) {
        int relationId = buffer.getInt();
        char newTuple = (char) buffer.get(); // 'N'

        Relation rel = cache.get(relationId);
        if (rel == null) {
            // Relation abhi tak nahi aaya, ye ho sakta hai agar slot purana ho
            // is case me skip kar do ya throw
            System.out.println("WARN: unknown relId " + relationId + " - R message missed?");
            return null;
        }

        short nCols = buffer.getShort();
        Map<String, Object> data = new LinkedHashMap<>();

        for (int i = 0; i < nCols; i++) {
            char colType = (char) buffer.get();
            String colName = rel.columns.get(i); 

            if (colType == 'n') {
                data.put(colName, null);
            } else if (colType == 't') {
                int len = buffer.getInt();
                byte[] bytes = new byte[len];
                buffer.get(bytes);
                data.put(colName, new String(bytes));
            } else if (colType == 'u') {
                // 'u' (TOAST unchanged) should NEVER appear in an INSERT — there's no
                // prior stored value for a brand-new row to be "unchanged" against.
                // Seeing this here almost certainly means buffer desync upstream.
                throw new IllegalStateException(
                    "Unexpected TOAST-unchanged marker in INSERT for column '" + colName +
                    "' (relId=" + relationId + ") — likely buffer desync, not valid protocol data.");
            } else {
                throw new IllegalStateException(
                    "Unknown column type marker '" + colType + "' for column '" + colName +
                    "' (relId=" + relationId + ") — buffer likely desynced.");
            }
        }

        return new CdcEvent(Operation.INSERT, rel.schema, rel.table, null, data, lsn);
    }

    /**
     * Reads a null-terminated (0x00 byte) string from the buffer (Postgres's wire format
     * for strings — bytes followed by a single 0x00 byte, no length prefix).
     * Consumes the terminating null byte too, leaving the cursor at the next field.
     */
    static String readString(ByteBuffer buffer) {
        StringBuilder sb = new StringBuilder();
        byte b;
        while (buffer.hasRemaining() && (b = buffer.get()) != 0) {
            sb.append((char) b);
        }
        return sb.toString();
    }
}
