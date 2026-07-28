package com.example.gameworkbench.director.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultDirectorToolRegistry implements DirectorToolRegistry {
    private final Map<String,DirectorTool> tools;
    private final DirectorResourceAuthorizer authorizer;
    private final DirectorToolResultStore results;
    private final ObjectMapper json;
    private final ExecutorService executor;
    private final ClosedJsonSchemaValidator schemas=new ClosedJsonSchemaValidator();
    private final Map<String,CachedResult> idempotency=new ConcurrentHashMap<>();

    public DefaultDirectorToolRegistry(List<DirectorTool> tools,DirectorResourceAuthorizer authorizer,
            DirectorToolResultStore results,ObjectMapper json,ExecutorService executor) {
        this.tools=tools.stream().collect(Collectors.toUnmodifiableMap(t->key(t.definition().name(),t.definition().version()),Function.identity()));
        this.authorizer=authorizer;this.results=results;this.json=json;this.executor=executor;
    }
    @Override public List<DirectorToolDefinition> discover(){return tools.values().stream().map(DirectorTool::definition).sorted(Comparator.comparing(DirectorToolDefinition::name)).toList();}
    @Override public ToolCallResult execute(DirectorToolContext context,ToolCallRequest request) {
        long started=System.nanoTime();
        if(context==null||request==null||request.callId()==null||request.idempotencyKey()==null) throw new BusinessException(ErrorCode.DIRECTOR_TOOL_INVALID);
        DirectorTool tool=tools.get(key(request.toolName(),request.toolVersion()));
        if(tool==null) throw new BusinessException(ErrorCode.DIRECTOR_TOOL_NOT_FOUND);
        try{schemas.validate(request.arguments(),tool.definition().argumentSchema());}catch(IllegalArgumentException e){throw new BusinessException(ErrorCode.DIRECTOR_TOOL_INVALID);}
        if(tool.definition().permission()!=ToolPermission.READ || !authorizer.mayRead(context.userId(),context.projectId(),request.toolName(),request.arguments()))
            throw new BusinessException(ErrorCode.DIRECTOR_TOOL_FORBIDDEN);
        String input=digest(bytes(request.arguments()));
        String idempotencyScope=context.projectId()+":"+context.runUuid()+":"+request.idempotencyKey();
        String requestFingerprint=digest((request.toolName()+"@"+request.toolVersion()+":"+input+":"+request.dryRun()).getBytes(StandardCharsets.UTF_8));
        CachedResult cached=idempotency.get(idempotencyScope);
        if(cached!=null){if(!cached.fingerprint().equals(requestFingerprint))throw new BusinessException(ErrorCode.DIRECTOR_TOOL_INVALID);return cached.result();}
        if(request.dryRun()) return new ToolCallResult(request.callId(),request.toolName(),request.toolVersion(),"DRY_RUN",input,null,"validated",null,elapsed(started),null);
        Future<JsonNode> future=executor.submit(()->tool.execute(context,request.arguments()));
        try {
            JsonNode output=future.get(tool.definition().timeoutMs(),TimeUnit.MILLISECONDS);
            byte[] body=bytes(output); String outputDigest=digest(body); String ref=results.put(context.projectId(),context.runUuid(),context.callId(),body);
            int limit=Math.min(tool.definition().maxInlineResultBytes(),2048);String summary=new String(body,0,Math.min(body.length,limit),StandardCharsets.UTF_8);
            ToolCallResult result=new ToolCallResult(request.callId(),request.toolName(),request.toolVersion(),"SUCCEEDED",input,outputDigest,summary,ref,elapsed(started),null);
            CachedResult raced=idempotency.putIfAbsent(idempotencyScope,new CachedResult(requestFingerprint,result));return raced==null?result:raced.result();
        } catch(TimeoutException e) {
            future.cancel(true);
            return new ToolCallResult(request.callId(),request.toolName(),request.toolVersion(),"TIMED_OUT",input,null,"",null,elapsed(started),ErrorCode.DIRECTOR_TOOL_TIMEOUT.name());
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw new IllegalStateException(e);
        } catch(ExecutionException e) {
            return new ToolCallResult(request.callId(),request.toolName(),request.toolVersion(),"FAILED",input,null,"",null,elapsed(started),"TOOL_EXECUTION_FAILED");
        }
    }
    private byte[] bytes(JsonNode node){try{return json.writeValueAsBytes(node);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    private long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
    private static String key(String name,String version){return name+"@"+version;}
    private record CachedResult(String fingerprint,ToolCallResult result){}
}
