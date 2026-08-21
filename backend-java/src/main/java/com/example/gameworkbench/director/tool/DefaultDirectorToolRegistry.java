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

/**
 * Director 类型化工具的发现、授权、校验、限时执行和结果摘要边界。
 *
 * <p>工具参数必须满足 additionalProperties=false 的闭合 schema，并通过项目资源授权。
 * 工具在独立有界线程池中执行；超时会取消 Future，但被调用实现仍应自行响应中断并保持幂等。</p>
 *
 * <p>当前结果正文和幂等缓存都在进程内。数据库保存的调用摘要不能替代完整结果，
 * 因此该实现还不具备跨重启的完整工具结果恢复能力。</p>
 */
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
        if(!authorizer.mayRead(context.userId(),context.projectId(),request.toolName(),request.arguments()))
            throw new BusinessException(ErrorCode.DIRECTOR_TOOL_FORBIDDEN);
        String input=digest(bytes(request.arguments()));
        String idempotencyScope=context.projectId()+":"+context.runUuid()+":"+request.idempotencyKey();
        String requestFingerprint=digest((request.toolName()+"@"+request.toolVersion()+":"+input+":"+request.dryRun()).getBytes(StandardCharsets.UTF_8));
        // INVARIANT: 相同运行作用域和幂等键只能对应同一个工具、版本、参数和 dry-run 模式。
        CachedResult cached=idempotency.get(idempotencyScope);
        if(cached!=null){if(!cached.fingerprint().equals(requestFingerprint))throw new BusinessException(ErrorCode.DIRECTOR_TOOL_INVALID);return cached.result();}
        if(request.dryRun()) return new ToolCallResult(request.callId(),request.toolName(),request.toolVersion(),"DRY_RUN",input,null,"validated",null,elapsed(started),null);
        Future<JsonNode> future=executor.submit(()->tool.execute(context,request.arguments()));
        try {
            JsonNode output=future.get(tool.definition().timeoutMs(),TimeUnit.MILLISECONDS);
            byte[] body=bytes(output); String outputDigest=digest(body); String ref=results.put(context.projectId(),context.runUuid(),context.callId(),body);
            // TODO(recovery): results 与 idempotency 当前都是内存实现；生产化时应以数据库唯一键
            // 和持久化对象存储作为事实源，使重启后的 resultRef 仍可读取。
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
