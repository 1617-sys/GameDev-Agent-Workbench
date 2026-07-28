package com.example.gameworkbench.director.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.domain.DirectorDecisionKind;
import com.example.gameworkbench.director.domain.DirectorRunStatus;
import com.example.gameworkbench.entity.DirectorDecisionRecord;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorDecisionMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.DirectorToolCallMapper;
import com.example.gameworkbench.mapper.ExperimentCandidateMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class DirectorRunServiceImpl implements DirectorRunService {
    private static final Set<String> ALLOWED_METRICS=Set.of("NOVICE_COMPLETION_RATE","REGULAR_COMPLETION_RATE",
        "EXPERT_COMPLETION_RATE","MEAN_COMPLETION_TIME","P50_COMPLETION_TIME","P95_COMPLETION_TIME",
        "TIMEOUT_RATE","DEATH_RATE","STUCK_RATE","VALID_ACTION_RATIO","PATH_EFFICIENCY",
        "PERSONA_PERFORMANCE_GAP","EXPERT_MEAN_COMPLETION_TIME_DELTA","TOKEN_USAGE","WALL_DURATION_MS","COST_MICROS");
    private final GameProjectMapper projects;
    private final DirectorRunMapper runs;
    private final DirectorDecisionMapper decisions;
    private final DirectorToolCallMapper toolCalls;
    private final ExperimentCandidateMapper candidates;
    private final ObjectMapper json;

    @Override @Transactional
    public DirectorRun create(long userId,long projectId,CreateDirectorRunCommand command) {
        ownedProject(userId,projectId);
        if(command == null || command.idempotencyKey() == null || !command.idempotencyKey().matches("[A-Za-z0-9._:@/-]{8,128}")) invalid();
        validateGoal(command.goal(),command.budget());
        String goalJson=write(command.goal()), budgetJson=write(command.budget());
        String fingerprint=digest(goalJson+"\n"+budgetJson);
        DirectorRun prior=runs.selectIdempotent(userId,projectId,command.idempotencyKey());
        if(prior!=null){
            if(!Objects.equals(prior.getRequestFingerprint(),fingerprint)) throw new BusinessException(ErrorCode.DIRECTOR_RUN_IDEMPOTENCY_CONFLICT);
            return prior;
        }
        LocalDateTime now=LocalDateTime.now();
        DirectorRun created=DirectorRun.builder().runUuid(UUID.randomUUID().toString()).userId(userId).projectId(projectId)
            .idempotencyKey(command.idempotencyKey()).requestFingerprint(fingerprint).goalJson(goalJson).goalDigest(digest(goalJson))
            .budgetJson(budgetJson).status(DirectorRunStatus.PENDING.name()).stateVersion(0L)
            .checkpointJson(initialCheckpoint(command.goal(),command.budget(),command.checkpoint())).createdAt(now).updatedAt(now).build();
        runs.insert(created); return created;
    }

    @Override @Transactional
    public DirectorRun appendDecision(long userId,long projectId,String runUuid,AppendDirectorDecisionCommand command) {
        DirectorRun run=ownedRun(userId,projectId,runUuid);
        DirectorRunStatus current=DirectorRunStatus.valueOf(run.getStatus());
        if(command==null || command.round()<1 || command.reasonSummary()==null || command.reasonSummary().length()>500
            || command.decisionDigest()==null || !command.decisionDigest().matches("[0-9a-f]{64}")
            || !decisionMatches(command.kind(),command.targetStatus()) || !current.canTransitionTo(command.targetStatus())) invalid();
        DirectorDecisionRecord record=DirectorDecisionRecord.builder().decisionUuid(UUID.randomUUID().toString())
            .directorRunId(run.getId()).projectId(projectId).roundNumber(command.round()).stateVersion(command.expectedStateVersion())
            .kind(command.kind().name()).reasonSummary(command.reasonSummary()).decisionDigest(command.decisionDigest())
            .modelEvidenceJson(write(command.modelEvidence())).payloadJson(write(command.payload())).createdAt(LocalDateTime.now()).build();
        decisions.insert(record);
        transitionChecked(run,projectId,command.expectedStateVersion(),command.targetStatus(),write(command.checkpoint()),
            command.approvalRef(),command.errorCode());
        return runs.selectByUuid(runUuid);
    }

    @Override @Transactional
    public DirectorRun transition(long userId,long projectId,String runUuid,long expectedVersion,String targetStatus,
            String checkpoint,String approvalRef,String errorCode) {
        DirectorRun run=ownedRun(userId,projectId,runUuid);
        DirectorRunStatus target;
        try { target=DirectorRunStatus.valueOf(targetStatus); } catch(Exception e) { invalid(); return null; }
        transitionChecked(run,projectId,expectedVersion,target,checkpoint,approvalRef,errorCode);
        return runs.selectByUuid(runUuid);
    }

    @Override @Transactional(readOnly=true)
    public DirectorRunView get(long userId,long projectId,String runUuid) {
        DirectorRun run=ownedRun(userId,projectId,runUuid);
        return new DirectorRunView(run,decisions.selectRunDecisions(run.getId(),projectId),
            toolCalls.selectRunCalls(run.getId(),projectId),candidates.selectRunCandidates(run.getId(),projectId));
    }

    private void transitionChecked(DirectorRun run,long projectId,long expectedVersion,DirectorRunStatus target,
            String checkpoint,String approvalRef,String errorCode) {
        DirectorRunStatus current=DirectorRunStatus.valueOf(run.getStatus());
        if(current.terminal() || !current.canTransitionTo(target) || !validCheckpoint(checkpoint) || checkpoint.length()>65_535) invalid();
        LocalDateTime now=LocalDateTime.now();
        int changed=runs.transition(run.getId(),projectId,expectedVersion,current.name(),target.name(),checkpoint,
            target==DirectorRunStatus.WAITING_APPROVAL?approvalRef:null,errorCode,now,target.terminal()?now:null);
        if(changed!=1) throw new BusinessException(ErrorCode.DIRECTOR_RUN_CONCURRENT_UPDATE);
    }

    private boolean decisionMatches(DirectorDecisionKind kind,DirectorRunStatus target) {
        if(kind==null || target==null) return false;
        return switch(kind) {
            case CALL_TOOL -> target==DirectorRunStatus.WAITING_EXPERIMENT;
            case REQUEST_APPROVAL -> target==DirectorRunStatus.WAITING_APPROVAL;
            case FINISH -> target==DirectorRunStatus.SUCCEEDED;
            case FAIL -> target==DirectorRunStatus.FAILED;
        };
    }

    private void validateGoal(JsonNode goal,JsonNode budget) {
        if(goal==null || !goal.isObject() || !"director/1.0".equals(goal.path("protocolVersion").asText())
            || !goal.path("sourceTextDigest").asText().matches("[0-9a-f]{64}")
            || !goal.path("metrics").isArray() || goal.path("metrics").isEmpty() || budget==null || !budget.isObject()) invalid();
        for(JsonNode metric:goal.path("metrics")){
            if(!ALLOWED_METRICS.contains(metric.path("name").asText()) || !metric.path("target").isObject())invalid();
            JsonNode target=metric.path("target");if(!target.has("min")&&!target.has("max"))invalid();
            if(target.has("min")&&target.has("max")&&target.path("min").asDouble()>target.path("max").asDouble())invalid();
        }
        String[] required={"maxRounds","maxToolCalls","maxCandidates","maxEpisodes","maxTokens","maxCostMicros","maxWallClockMs","maxFailures"};
        for(String key:required) if(!budget.has(key) || !budget.path(key).canConvertToLong() || budget.path(key).asLong()<0) invalid();
    }
    private String initialCheckpoint(JsonNode goal,JsonNode budget,JsonNode supplied){
        var checkpoint=json.createObjectNode();JsonNode normalizedGoal=goal.deepCopy();if(normalizedGoal instanceof com.fasterxml.jackson.databind.node.ObjectNode object&&!object.has("budget"))object.set("budget",budget.deepCopy());checkpoint.set("goal",normalizedGoal);checkpoint.set("budget",budget.deepCopy());
        checkpoint.set("usage",json.createObjectNode());checkpoint.put("lastCompletedRound",0);checkpoint.set("recentToolResults",json.createArrayNode());
        checkpoint.set("candidates",json.createArrayNode());checkpoint.putNull("pendingApproval");
        if(supplied!=null&&!supplied.isNull())checkpoint.set("facts",supplied.deepCopy());return write(checkpoint);
    }
    private boolean validCheckpoint(String value){try{JsonNode node=json.readTree(value);return node!=null&&node.isObject()&&node.has("goal")&&node.has("budget")&&node.has("usage")&&node.has("lastCompletedRound")&&node.has("recentToolResults")&&node.has("candidates");}catch(Exception e){return false;}}
    private DirectorRun ownedRun(long userId,long projectId,String uuid) {
        ownedProject(userId,projectId); DirectorRun run=runs.selectByUuid(uuid);
        if(run==null) throw new BusinessException(ErrorCode.DIRECTOR_RUN_NOT_FOUND);
        if(run.getProjectId()!=projectId || run.getUserId()!=userId) throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        return run;
    }
    private void ownedProject(long userId,long projectId) {
        GameProject project=projects.selectById(projectId);
        if(project==null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        if(!Objects.equals(project.getUserId(),userId)) throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void invalid(){throw new BusinessException(ErrorCode.DIRECTOR_RUN_INVALID);}
}
