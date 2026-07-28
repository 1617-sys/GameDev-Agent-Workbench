package com.example.gameworkbench.director.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDirectorToolResultStore implements DirectorToolResultStore {
    private final Map<String,byte[]> values=new ConcurrentHashMap<>();
    @Override public String put(long projectId,String runUuid,String callId,byte[] content) {
        String ref="director-result://"+projectId+"/"+runUuid+"/"+callId;
        values.put(ref,content.clone()); return ref;
    }
    public byte[] get(String ref){byte[] value=values.get(ref);return value==null?null:value.clone();}
}
