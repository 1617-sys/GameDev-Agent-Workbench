package com.example.gameworkbench.director.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.domain.DirectorDecisionKind;
import com.example.gameworkbench.director.domain.DirectorRunStatus;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.DirectorDecisionRecord;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorDecisionMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.DirectorToolCallMapper;
import com.example.gameworkbench.mapper.ExperimentCandidateMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class DirectorRunServiceImplTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void createsIdempotentlyAndRejectsConflictingFingerprint(){
        Fixture f=fixture(); when(f.runs.selectIdempotent(7L,1L,"director-key-001")).thenReturn(null);
        DirectorRun created=f.service.create(7L,1L,new CreateDirectorRunCommand("director-key-001",goal(),budget(),json.createObjectNode()));
        assertThat(created.getStatus()).isEqualTo("PENDING");verify(f.runs).insert(any(DirectorRun.class));
        DirectorRun prior=DirectorRun.builder().requestFingerprint("different").build();when(f.runs.selectIdempotent(7L,1L,"director-key-002")).thenReturn(prior);
        assertThatThrownBy(()->f.service.create(7L,1L,new CreateDirectorRunCommand("director-key-002",goal(),budget(),json.createObjectNode()))).isInstanceOf(BusinessException.class);
    }

    @Test void persistsDecisionAndUsesOptimisticLock(){
        Fixture f=fixture();DirectorRun run=run("RUNNING",2L);when(f.runs.selectByUuid("run")).thenReturn(run);
        when(f.runs.transition(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyString(),any(),any(),any(),any())).thenReturn(0);
        AppendDirectorDecisionCommand command=new AppendDirectorDecisionCommand(2,3,DirectorDecisionKind.CALL_TOOL,"read baseline","a".repeat(64),json.createObjectNode(),json.createObjectNode(),DirectorRunStatus.WAITING_EXPERIMENT,checkpoint(),null,null);
        assertThatThrownBy(()->f.service.appendDecision(7L,1L,"run",command)).isInstanceOf(BusinessException.class);
        verify(f.decisions).insert(any(DirectorDecisionRecord.class));
    }

    @Test void rejectsTerminalResumeAndCrossProjectAccess(){
        Fixture f=fixture();when(f.runs.selectByUuid("run")).thenReturn(run("SUCCEEDED",3L));
        assertThatThrownBy(()->f.service.transition(7L,1L,"run",3,"RUNNING","{}",null,null)).isInstanceOf(BusinessException.class);
        GameProject foreign=new GameProject();foreign.setId(1L);foreign.setUserId(99L);when(f.projects.selectById(1L)).thenReturn(foreign);
        assertThatThrownBy(()->f.service.get(7L,1L,"run")).isInstanceOf(BusinessException.class);
        verify(f.toolCalls,never()).selectRunCalls(anyLong(),anyLong());
    }

    private Fixture fixture(){GameProjectMapper projects=mock(GameProjectMapper.class);DirectorRunMapper runs=mock(DirectorRunMapper.class);DirectorDecisionMapper decisions=mock(DirectorDecisionMapper.class);DirectorToolCallMapper calls=mock(DirectorToolCallMapper.class);ExperimentCandidateMapper candidates=mock(ExperimentCandidateMapper.class);GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);when(projects.selectById(1L)).thenReturn(project);return new Fixture(new DirectorRunServiceImpl(projects,runs,decisions,calls,candidates,json),projects,runs,decisions,calls);}
    private DirectorRun run(String status,long version){return DirectorRun.builder().id(10L).runUuid("run").userId(7L).projectId(1L).status(status).stateVersion(version).build();}
    private ObjectNode goal(){ObjectNode value=json.createObjectNode().put("protocolVersion","director/1.0").put("sourceTextDigest","a".repeat(64));value.putArray("metrics").addObject().put("name","NOVICE_COMPLETION_RATE").putObject("target").put("min",.55);return value;}
    private ObjectNode budget(){ObjectNode value=json.createObjectNode();for(String key:List.of("maxRounds","maxToolCalls","maxCandidates","maxEpisodes","maxTokens","maxCostMicros","maxWallClockMs","maxFailures"))value.put(key,1);return value;}
    private ObjectNode checkpoint(){ObjectNode value=json.createObjectNode();value.set("goal",goal());value.set("budget",budget());value.set("usage",json.createObjectNode());value.put("lastCompletedRound",2);value.set("recentToolResults",json.createArrayNode());value.set("candidates",json.createArrayNode());return value;}
    private record Fixture(DirectorRunServiceImpl service,GameProjectMapper projects,DirectorRunMapper runs,DirectorDecisionMapper decisions,DirectorToolCallMapper toolCalls){}
}
