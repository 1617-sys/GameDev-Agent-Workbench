package com.example.gameworkbench.director.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单进程演示用的 Director 工具结果存储。
 *
 * <p>内容会防御性复制，但服务重启后全部丢失，不能作为生产环境的审计或恢复事实源。</p>
 */
public class InMemoryDirectorToolResultStore implements DirectorToolResultStore {
    private final Map<String,byte[]> values=new ConcurrentHashMap<>();
    @Override public String put(long projectId,String runUuid,String callId,byte[] content) {
        String ref="director-result://"+projectId+"/"+runUuid+"/"+callId;
        values.put(ref,content.clone()); return ref;
    }
    public byte[] get(String ref){byte[] value=values.get(ref);return value==null?null:value.clone();}
}
