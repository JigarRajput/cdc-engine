package com.jigar.engine;

import java.util.concurrent.ConcurrentHashMap;

import com.jigar.model.Relation;

public class RelationCache {
    private final ConcurrentHashMap<Integer, Relation> map = new ConcurrentHashMap<>();
    public void put(Relation r){ map.put(r.id, r); }
    public Relation get(int id){ return map.get(id); }
}