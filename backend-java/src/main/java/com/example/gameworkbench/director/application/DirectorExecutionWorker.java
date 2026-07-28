package com.example.gameworkbench.director.application;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.example.gameworkbench.director.client.DirectorDecisionClient;
import com.example.gameworkbench.director.domain.DirectorDecisionKind;
import com.example.gameworkbench.director.domain.DirectorRunStatus;
import com.example.gameworkbench.director.persistence.AppendDirectorDecisionCommand;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.director.tool.DirectorToolContext;
import com.example.gameworkbench.director.tool.DirectorToolRegistry;
import com.example.gameworkbench.director.tool.ToolCallRequest;
import com.example.gameworkbench.director.tool.ToolCallResult;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.DirectorRunEvent;
import com.example.gameworkbench.entity.DirectorToolCallRecord;
import com.example.gameworkbench.mapper.DirectorDecisionMapper;
import com.example.gameworkbench.mapper.DirectorRunEventMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.DirectorToolCallMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class DirectorExecutionWorker {
    private final DirectorRunMapper runs;
    private final DirectorDecisionMapper decisions;
    private final DirectorToolCallMapper calls;
    private final DirectorRunEventMapper events;
    private final DirectorRunService runService;
    private final DirectorDecisionClient director;
    private final DirectorToolRegistry tools;
    private final ObjectMapper json;
    @Value("${app.director.claim-seconds:60}") private long claimSeconds;
    @Value("${app.director.max-attempts:3}") private int maxAttempts;

    @Async
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void requested(DirectorRunRequested event){execute(event.runUuid());}

    @Scheduled(fixedDelayString="${app.director.recovery-delay-ms:30000}")
    public void recover(){runs.selectRecoverable(LocalDateTime.now(),20).forEach(run->execute(run.getRunUuid()));}

    public void execute(String runUuid){
        for(int guard=0;guard<100;guard++){
            DirectorRun run=runs.selectByUuid(runUuid);
            if(run==null||!("RUNNING".equals(run.getStatus())||recoverableWaiting(run)))return;
            String token=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now();
            if(runs.claim(runUuid,run.getStateVersion(),token,now.plusSeconds(claimSeconds),now)!=1)return;
            run=runs.selectByUuid(runUuid);
            try{
                ObjectNode checkpoint=checkpoint(run);
                if("WAITING_EXPERIMENT".equals(run.getStatus())){executePending(run,checkpoint,token);return;}
                ObjectNode usage=(ObjectNode)checkpoint.path("usage");if(run.getCreatedAt()!=null)usage.put("wallClockMs",Math.max(0,Duration.between(run.getCreatedAt(),LocalDateTime.now()).toMillis()));String exhausted=exhausted(checkpoint);
                if(exhausted!=null){runService.transition(run.getUserId(),run.getProjectId(),runUuid,run.getStateVersion(),"FAILED",write(checkpoint),null,"BUDGET_"+exhausted+"_EXHAUSTED");event(run,"BUDGET_EXHAUSTED",exhausted+" budget exhausted");return;}
                int used=usage.path("rounds").asInt(0);
                JsonNode decision=director.decide(snapshot(run,checkpoint),trace(run));String kind=decision.path("kind").asText();int round=decision.path("round").asInt();
                if(round!=used+1)throw new IllegalArgumentException("Director round is not sequential");
                updateCheckpointForDecision(checkpoint,round,decision);
                if("CALL_TOOL".equals(kind))checkpoint.set("pendingToolCall",decision.path("toolCall").deepCopy());
                String approvalRef=approvalRef(decision,checkpoint,kind);JsonNode payload=payload(decision,kind);DirectorRunStatus target=target(kind);
                runService.appendDecision(run.getUserId(),run.getProjectId(),runUuid,new AppendDirectorDecisionCommand(run.getStateVersion(),round,DirectorDecisionKind.valueOf(kind),safe(decision.path("reasonSummary").asText()),decision.path("decisionDigest").asText(),decision.path("modelEvidence"),payload,target,checkpoint,approvalRef,"FAIL".equals(kind)?decision.path("error").path("code").asText("DIRECTOR_FAILED"):null));
                event(run,"DECISION_"+kind,safe(decision.path("reasonSummary").asText()));
                if(!"CALL_TOOL".equals(kind))return;
                DirectorRun waiting=runs.selectByUuid(runUuid);boolean continueLoop=executePending(waiting,checkpoint,token);if(!continueLoop)return;
            }catch(Exception error){handleFailure(runUuid,token,error);return;}
        }
    }

    private boolean executePending(DirectorRun run,ObjectNode checkpoint,String token){
        JsonNode call=checkpoint.path("pendingToolCall");if(!call.isObject())return false;
        ToolCallRequest request=new ToolCallRequest(call.path("callId").asText(),call.path("toolName").asText(),call.path("toolVersion").asText(),call.path("idempotencyKey").asText(),call.path("arguments"),call.path("dryRun").asBoolean(false));
        DirectorToolCallRecord persisted=calls.selectByCallUuid(request.callId());ToolCallResult result;
        if(persisted==null){result=tools.execute(new DirectorToolContext(run.getUserId(),run.getProjectId(),run.getRunUuid(),request.callId()),request);persistCall(run,request,result,checkpoint.path("lastCompletedRound").asInt());}
        else result=new ToolCallResult(persisted.getCallUuid(),persisted.getToolName(),persisted.getToolVersion(),persisted.getStatus(),persisted.getInputDigest(),persisted.getOutputDigest(),persisted.getOutputSummary(),persisted.getResultRef(),persisted.getDurationMs(),persisted.getErrorCode());
        updateToolUsage(checkpoint,request,result);appendResult(checkpoint,result);checkpoint.remove("pendingToolCall");LocalDateTime now=LocalDateTime.now();
        if("RUN_PLAYER_EXPERIMENT".equals(request.toolName())&&"SUCCEEDED".equals(result.status())){if(runs.checkpointWaiting(run.getRunUuid(),run.getStateVersion(),token,write(checkpoint),now)!=1)throw new IllegalStateException("waiting checkpoint lost ownership");return false;}
        runService.transition(run.getUserId(),run.getProjectId(),run.getRunUuid(),run.getStateVersion(),"RUNNING",write(checkpoint),null,result.errorCode());runs.releaseSuccessfulClaim(run.getRunUuid(),token,now);return true;
    }

    private void persistCall(DirectorRun run,ToolCallRequest request,ToolCallResult result,int round){var decision=decisions.selectRound(run.getId(),round);LocalDateTime now=LocalDateTime.now();calls.insert(DirectorToolCallRecord.builder().callUuid(request.callId()).directorRunId(run.getId()).decisionId(decision.getId()).projectId(run.getProjectId()).toolName(request.toolName()).toolVersion(request.toolVersion()).idempotencyKey(request.idempotencyKey()).status(result.status()).inputDigest(result.inputDigest()).inputSummary(safe(request.arguments().toString())).outputDigest(result.outputDigest()).outputSummary(safe(result.summary())).resultRef(result.resultRef()).durationMs(result.durationMs()).errorCode(result.errorCode()).retryCount(0).createdAt(now).completedAt(now).build());}
    private ObjectNode snapshot(DirectorRun run,ObjectNode checkpoint){ObjectNode out=json.createObjectNode().put("protocolVersion","director/1.0").put("runId",run.getRunUuid()).put("projectId",String.valueOf(run.getProjectId())).put("stateVersion",run.getStateVersion()).put("status",run.getStatus());out.set("goal",checkpoint.path("goal"));out.set("usage",checkpoint.path("usage"));ArrayNode allowed=out.putArray("allowedTools");tools.discover().forEach(d->{ObjectNode t=allowed.addObject().put("name",d.name()).put("version",d.version()).put("permission",d.permission().name()).put("riskLevel",d.riskLevel().name()).put("timeoutMs",d.timeoutMs());t.set("argumentSchema",d.argumentSchema());});out.set("recentToolResults",checkpoint.path("recentToolResults"));JsonNode facts=checkpoint.path("facts");if(facts.isObject())facts.fields().forEachRemaining(entry->out.set(entry.getKey(),entry.getValue()));return out;}
    private void appendResult(ObjectNode checkpoint,ToolCallResult result){ArrayNode array=checkpoint.withArray("recentToolResults");if(array.size()>=20)array.remove(0);ObjectNode node=array.addObject().put("callId",result.callId()).put("toolName",result.toolName()).put("status",result.status()).put("outputDigest",result.outputDigest()==null?"0".repeat(64):result.outputDigest()).put("resultRef",result.resultRef());node.putObject("summary").put("preview",safe(result.summary()));if("REQUEST_HUMAN_APPROVAL".equals(result.toolName()))try{JsonNode body=json.readTree(result.summary());if(body.path("approvalRef").isTextual())checkpoint.put("pendingApproval",body.path("approvalRef").asText());}catch(Exception ignored){}ObjectNode usage=(ObjectNode)checkpoint.path("usage");usage.put("toolCalls",usage.path("toolCalls").asInt()+1);if(!"SUCCEEDED".equals(result.status())&&!"DRY_RUN".equals(result.status()))usage.put("failures",usage.path("failures").asInt()+1);}
    private void updateCheckpointForDecision(ObjectNode checkpoint,int round,JsonNode decision){ObjectNode usage=(ObjectNode)checkpoint.path("usage");usage.put("rounds",round);usage.put("tokens",usage.path("tokens").asLong()+decision.path("modelEvidence").path("tokenUsage").asLong(0));usage.put("costMicros",usage.path("costMicros").asLong()+decision.path("modelEvidence").path("costMicros").asLong(0));checkpoint.put("lastCompletedRound",round);}
    private void updateToolUsage(ObjectNode checkpoint,ToolCallRequest request,ToolCallResult result){if(!"SUCCEEDED".equals(result.status()))return;ObjectNode usage=(ObjectNode)checkpoint.path("usage");if("RUN_PLAYER_EXPERIMENT".equals(request.toolName()))usage.put("episodes",usage.path("episodes").asInt()+request.arguments().path("personas").size()*request.arguments().path("seeds").size()*2);if("GENERATE_NEIGHBOR_CANDIDATES".equals(request.toolName()))try{usage.put("candidates",usage.path("candidates").asInt()+json.readTree(result.summary()).path("candidates").size());}catch(Exception ignored){}}
    private String approvalRef(JsonNode decision,ObjectNode checkpoint,String kind){if(!"REQUEST_APPROVAL".equals(kind))return null;String ref=decision.path("approval").path("approvalRef").asText(null);if(ref==null&&checkpoint.path("pendingApproval").isTextual())ref=checkpoint.path("pendingApproval").asText();if(ref==null&&decision.path("approval").path("prototypeVersionUuid").isTextual())ref="approval://"+decision.path("approval").path("prototypeVersionUuid").asText();if(ref==null)throw new IllegalArgumentException("approval reference is required");return ref;}
    private void handleFailure(String runUuid,String token,Exception error){runs.releaseClaim(runUuid,token,LocalDateTime.now());DirectorRun failed=runs.selectByUuid(runUuid);if(failed!=null&&!DirectorRunStatus.valueOf(failed.getStatus()).terminal()&&failed.getExecutionAttempt()!=null&&failed.getExecutionAttempt()>=maxAttempts)try{runService.transition(failed.getUserId(),failed.getProjectId(),runUuid,failed.getStateVersion(),"FAILED",failed.getCheckpointJson(),null,error.getClass().getSimpleName());}catch(Exception ignored){}}
    private boolean recoverableWaiting(DirectorRun run){return "WAITING_EXPERIMENT".equals(run.getStatus())&&run.getCheckpointJson()!=null&&run.getCheckpointJson().contains("pendingToolCall");}
    private String exhausted(ObjectNode checkpoint){JsonNode usage=checkpoint.path("usage"),budget=checkpoint.path("budget");String[][] dimensions={{"rounds","maxRounds","ROUNDS"},{"toolCalls","maxToolCalls","TOOL_CALLS"},{"candidates","maxCandidates","CANDIDATES"},{"episodes","maxEpisodes","EPISODES"},{"tokens","maxTokens","TOKENS"},{"costMicros","maxCostMicros","COST"},{"wallClockMs","maxWallClockMs","WALL_CLOCK"},{"failures","maxFailures","FAILURES"}};for(String[] dimension:dimensions)if(budget.has(dimension[1])&&usage.path(dimension[0]).asLong()>=budget.path(dimension[1]).asLong())return dimension[2];return null;}
    private JsonNode payload(JsonNode decision,String kind){return switch(kind){case "CALL_TOOL"->decision.path("toolCall");case "REQUEST_APPROVAL"->decision.path("approval");case "FINISH"->decision.path("outcome");case "FAIL"->decision.path("error");default->throw new IllegalArgumentException("invalid decision kind");};}
    private DirectorRunStatus target(String kind){return switch(kind){case "CALL_TOOL"->DirectorRunStatus.WAITING_EXPERIMENT;case "REQUEST_APPROVAL"->DirectorRunStatus.WAITING_APPROVAL;case "FINISH"->DirectorRunStatus.SUCCEEDED;case "FAIL"->DirectorRunStatus.FAILED;default->throw new IllegalArgumentException("invalid decision kind");};}
    private ObjectNode checkpoint(DirectorRun run){try{return (ObjectNode)json.readTree(run.getCheckpointJson());}catch(Exception e){throw new IllegalStateException(e);}}
    private void event(DirectorRun run,String type,String summary){events.insert(DirectorRunEvent.builder().eventUuid(UUID.randomUUID().toString()).directorRunId(run.getId()).projectId(run.getProjectId()).eventType(type).stateVersion(run.getStateVersion()).traceId(trace(run)).detailSummary(safe(summary)).createdAt(LocalDateTime.now()).build());}
    private String trace(DirectorRun run){return run.getTraceId()==null?UUID.randomUUID().toString():run.getTraceId();}
    private String safe(String value){if(value==null)return "";return value.length()>500?value.substring(0,500):value;}
    private String write(JsonNode value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
