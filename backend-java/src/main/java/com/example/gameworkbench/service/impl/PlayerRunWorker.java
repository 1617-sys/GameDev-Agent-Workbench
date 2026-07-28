package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.example.gameworkbench.client.PlayerApiClient;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeResultRequest;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.service.MachineEpisodeService;
import com.example.gameworkbench.service.impl.PlayerRunServiceImpl.PlayerRunRequested;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlayerRunWorker {
    private final PlayerRunMapper runs;
    private final PlayerApiClient playerApi;
    private final MachineEpisodeService episodes;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    @Autowired public PlayerRunWorker(PlayerRunMapper runs,PlayerApiClient playerApi,MachineEpisodeService episodes,ObjectMapper json,ApplicationEventPublisher events){this.runs=runs;this.playerApi=playerApi;this.episodes=episodes;this.json=json;this.events=events;}
    public PlayerRunWorker(PlayerRunMapper runs,PlayerApiClient playerApi,MachineEpisodeService episodes,ObjectMapper json){this(runs,playerApi,episodes,json,event->{});}

    @Async
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void requested(PlayerRunRequested event){execute(event.runUuid());}

    @Scheduled(fixedDelayString="${app.player.recovery-delay-ms:30000}")
    public void recover(){runs.selectRunnable(20).forEach(run->execute(run.getRunUuid()));}

    public void execute(String runUuid){
        LocalDateTime now=LocalDateTime.now();if(runs.claim(runUuid,now)!=1)return;
        PlayerRun run=runs.selectByUuid(runUuid);if(run==null)return;
        try{
            JsonNode response=run.getResponseJson()==null?playerApi.runBatch(read(run.getRequestJson()),run.getTraceId()):read(run.getResponseJson());
            if(run.getResponseJson()==null){runs.saveResponse(runUuid,write(response),LocalDateTime.now());run.setResponseJson(write(response));}
            PersistMachineEpisodeBatchRequest persist=new PersistMachineEpisodeBatchRequest();persist.setEpisodeProtocolVersion("episode/1.0");persist.setClientBatchKey(run.getClientBatchKey());
            java.util.List<PersistMachineEpisodeResultRequest> values=new java.util.ArrayList<>();for(JsonNode result:response.path("results"))values.add(map(result));persist.setEpisodes(values);
            var batch=episodes.persistBatch(run.getUserId(),run.getProjectUuid(),"player-run/"+runUuid,persist);
            runs.complete(runUuid,batch.getStatus(),batch.getBatchId(),LocalDateTime.now());
            events.publishEvent(new PlayerRunCompleted(runUuid,batch.getStatus()));
        }catch(Exception exception){if(run.getAttempt()<3)runs.retry(runUuid,"PLAYER_EXECUTION_RETRY",safe(exception),LocalDateTime.now());else{runs.fail(runUuid,"PLAYER_EXECUTION_FAILED",safe(exception),LocalDateTime.now());events.publishEvent(new PlayerRunCompleted(runUuid,"FAILED"));}}
    }

    private PersistMachineEpisodeResultRequest map(JsonNode value){
        PersistMachineEpisodeResultRequest out=new PersistMachineEpisodeResultRequest();JsonNode prototype=value.path("prototype"),simulation=value.path("simulation"),policy=value.path("policy"),persona=value.path("persona"),timing=value.path("timing");
        out.setEpisodeId(value.path("episodeId").asText());out.setClientEpisodeKey(value.path("clientEpisodeKey").asText());out.setPrototypeVersionUuid(prototype.path("prototypeVersionUuid").asText());out.setConfigDigest(prototype.path("configDigest").asText());
        out.setSimulationProtocolVersion(simulation.path("protocolVersion").asText());out.setCoreVersion(simulation.path("coreVersion").asText());out.setSeed(simulation.path("seed").longValue());out.setMaxSteps(simulation.path("maxSteps").intValue());out.setObservationPolicy(simulation.path("observationPolicy"));
        out.setPolicyId(policy.path("policyId").asText());out.setPolicyVersion(policy.path("policyVersion").asText());out.setPolicyDigest(policy.path("policyDigest").asText());out.setPersonaId(persona.path("personaId").asText());out.setPersonaVersion(persona.path("personaVersion").asText());out.setPersonaDigest(persona.path("personaDigest").asText());
        out.setModel(nullable(value.get("model")));out.setUsage(nullable(value.get("usage")));out.setAudit(nullable(value.get("audit")));out.setTiming(nullable(value.get("timing")));out.setError(nullable(value.get("error")));out.setMetricVersion(value.path("metricVersion").asText());out.setExecutionStatus(value.path("executionStatus").asText());out.setTerminationReason(textOrNull(value.get("terminationReason")));out.setOutcome(textOrNull(value.get("outcome")));
        out.setStepCount(value.path("stepCount").intValue());out.setAcceptedActionCount(value.path("acceptedActionCount").intValue());out.setInvalidActionCount(value.path("invalidActionCount").intValue());out.setFinalStateHash(textOrNull(value.get("finalStateHash")));out.setFinalScore(value.path("finalScore").isNumber()?value.path("finalScore").intValue():null);out.setTrajectoryDigest(textOrNull(value.get("trajectoryDigest")));out.setWallDurationMs(timing.path("wallDurationMs").isNumber()?timing.path("wallDurationMs").longValue():null);
        java.util.List<JsonNode> steps=new java.util.ArrayList<>();value.path("steps").forEach(step->steps.add(step.deepCopy()));out.setSteps(steps);return out;
    }
    private JsonNode nullable(JsonNode value){return value==null||value.isNull()?null:value.deepCopy();}
    private String textOrNull(JsonNode value){return value==null||value.isNull()?null:value.asText();}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(JsonNode value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String safe(Exception error){String name=error.getClass().getSimpleName();return name.length()>200?name.substring(0,200):name;}
}
