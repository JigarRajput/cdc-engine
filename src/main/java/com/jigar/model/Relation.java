package com.jigar.model;
import java.util.List;

public class Relation {
    public final int id;
    public final String schema;
    public final String table;
    public final List<String> columns; // abhi ke liye empty, baad me bharenge
    public Relation(int id, String schema, String table, List<String> columns) {
        this.id=id; this.schema=schema; this.table=table; this.columns=columns;
    }
}