package com.example.gameworkbench.director.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.gameworkbench.director.client.DirectorDecisionClient;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.director.tool.DirectorToolRegistry;
import com.example.gameworkbench.director.tool.ToolCallResult;
import com.example.gameworkbench.entity.DirectorDecisionRecord;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.DirectorToolCallRecord;
import com.example.gameworkbench.mapper.DirectorDecisionMapper;
import com.example.gameworkbench.mapper.DirectorRunEventMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.DirectorToolCallMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class DirectorExecutionWorkerTest {
    @Test void fakeDirectorAndToolExecuteOnePersistedRoundThenResume(){
        Fixture f=fixture();DirectorRun running=run("RUNNING",1),waiting=run("WAITING_EXPERIMENT",2);
        when(f.runs.selectByUuid("run")).thenReturn(running,running,waiting,null);
        when(f.runs.claim(eq("run"),eq(1L),any(),any(),any())).thenReturn(1);
        when(f.client.decide(any(),any())).thenReturn(decision(f.json));
        when(f.decisions.selectRound(10L,1)).thenReturn(DirectorDecisionRecord.builder().id(20L).build());
        when(f.tools.discover()).thenReturn(List.of());
        when(f.tools.execute(any(),any())).thenReturn(new ToolCallResult("call-1","GET_PROTOTYPE_VERSION","1","SUCCEEDED","a".repeat(64),"b".repeat(64),"{}","result://1",2,null));
        f.worker.execute("run");
        verify(f.client).decide(any(),any());verify(f.tools).execute(any(),any());verify(f.calls).insert(any(DirectorToolCallRecord.class));
        verify(f.service).appendDecision(any(Long.class),any(Long.class),eq("run"),any());
        verify(f.service).transition(any(Long.class),any(Long.class),eq("run"),eq(2L),eq("RUNNING"),any(),any(),any());
    }
    @Test void cancellationNeverCallsPythonOrTools(){Fixture f=fixture();when(f.runs.selectByUuid("canceled")).thenReturn(run("CANCELED",2));f.worker.execute("canceled");verify(f.client,never()).decide(any(),any());verify(f.tools,never()).execute(any(),any());}
    @Test void exhaustedBudgetFailsWithoutAnotherModelCall(){Fixture f=fixture();DirectorRun exhausted=run("RUNNING",1);exhausted.setCheckpointJson(checkpoint(f.json,2,2));when(f.runs.selectByUuid("run")).thenReturn(exhausted,exhausted);when(f.runs.claim(eq("run"),eq(1L),any(),any(),any())).thenReturn(1);f.worker.execute("run");verify(f.service).transition(7L,1L,"run",1L,"FAILED",exhausted.getCheckpointJson(),null,"BUDGET_ROUNDS_EXHAUSTED");verify(f.client,never()).decide(any(),any());}
    private Fixture fixture(){ObjectMapper json=new ObjectMapper();DirectorRunMapper runs=mock(DirectorRunMapper.class);DirectorDecisionMapper decisions=mock(DirectorDecisionMapper.class);DirectorToolCallMapper calls=mock(DirectorToolCallMapper.class);DirectorRunEventMapper events=mock(DirectorRunEventMapper.class);DirectorRunService service=mock(DirectorRunService.class);DirectorDecisionClient client=mock(DirectorDecisionClient.class);DirectorToolRegistry tools=mock(DirectorToolRegistry.class);DirectorExecutionWorker worker=new DirectorExecutionWorker(runs,decisions,calls,events,service,client,tools,json);ReflectionTestUtils.setField(worker,"claimSeconds",60L);ReflectionTestUtils.setField(worker,"maxAttempts",3);return new Fixture(worker,runs,decisions,calls,service,client,tools,json);}
    private DirectorRun run(String status,long version){ObjectMapper json=new ObjectMapper();return DirectorRun.builder().id(10L).runUuid("run").userId(7L).projectId(1L).status(status).stateVersion(version).traceId("trace-1234").executionAttempt(1).checkpointJson(checkpoint(json,0,5)).build();}
    private String checkpoint(ObjectMapper json,int rounds,int max){try{var root=json.createObjectNode();root.putObject("goal").put("protocolVersion","director/1.0");root.putObject("budget").put("maxRounds",max);root.putObject("usage").put("rounds",rounds);root.put("lastCompletedRound",rounds);root.putArray("recentToolResults");root.putArray("candidates");return json.writeValueAsString(root);}catch(Exception e){throw new IllegalStateException(e);}}
    private com.fasterxml.jackson.databind.JsonNode decision(ObjectMapper json){var root=json.createObjectNode().put("kind","CALL_TOOL").put("round",1).put("reasonSummary","read").put("decisionDigest","d".repeat(64));root.putObject("modelEvidence");root.putObject("toolCall").put("callId","call-1").put("toolName","GET_PROTOTYPE_VERSION").put("toolVersion","1").put("idempotencyKey","run:1").put("dryRun",false).putObject("arguments").put("prototypeVersionUuid","v1");return root;}
    private record Fixture(DirectorExecutionWorker worker,DirectorRunMapper runs,DirectorDecisionMapper decisions,DirectorToolCallMapper calls,DirectorRunService service,DirectorDecisionClient client,DirectorToolRegistry tools,ObjectMapper json){}
}
