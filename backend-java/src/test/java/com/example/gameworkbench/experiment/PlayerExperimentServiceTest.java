package com.example.gameworkbench.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.ExperimentComparison;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.MachineEpisode;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.service.PlayerRunService;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlayerExperimentServiceTest {
    @Test void deterministicComparisonRecommendsOnlyWhenTargetAndGuardrailPass(){Fixture f=fixture();add(f,"l1",episode("l1","base","NOVICE",1,"SUCCESS",100));add(f,"l2",episode("l2","base","EXPERT",2,"SUCCESS",100));add(f,"l3",episode("l3","base","NOVICE",3,"ERROR",100));add(f,"r1",episode("r1","candidate","NOVICE",1,"SUCCESS",105));add(f,"r2",episode("r2","candidate","EXPERT",2,"SUCCESS",105));add(f,"r3",episode("r3","candidate","NOVICE",3,"SUCCESS",105));var args=f.json.createObjectNode().put("baselineVersionUuid","base").put("candidateVersionUuid","candidate").put("minimumSamples",3).put("minimumCompletionRate",.8).put("maxExpertMeanTimeDelta",.08);args.putArray("baselineEpisodeUuids").add("l1").add("l2").add("l3");args.putArray("candidateEpisodeUuids").add("r1").add("r2").add("r3");var result=f.service.compare(7,1,"run",args);assertThat(result.path("comparable").asBoolean()).isTrue();assertThat(result.path("recommended").asBoolean()).isTrue();ArgumentCaptor<ExperimentComparison> saved=ArgumentCaptor.forClass(ExperimentComparison.class);verify(f.comparisons).insert(saved.capture());assertThat(saved.getValue().getEpisodeRefsJson()).contains("l1","r3");}
    @Test void partialEpisodeMakesWindowExplicitlyNotComparable(){Fixture f=fixture();MachineEpisode partial=episode("l1","base","NOVICE",1,"SUCCESS",100);partial.setExecutionStatus("FAILED");add(f,"l1",partial);add(f,"r1",episode("r1","candidate","NOVICE",1,"SUCCESS",100));var args=f.json.createObjectNode().put("baselineVersionUuid","base").put("candidateVersionUuid","candidate").put("minimumSamples",1).put("minimumCompletionRate",0).put("maxExpertMeanTimeDelta",1);args.putArray("baselineEpisodeUuids").add("l1");args.putArray("candidateEpisodeUuids").add("r1");assertThat(f.service.compare(7,1,"run",args).path("reason").asText()).isEqualTo("PARTIAL_OR_FAILED_EPISODES");}
    private Fixture fixture(){ObjectMapper json=new ObjectMapper();GameProjectMapper projects=mock(GameProjectMapper.class);DirectorRunMapper runs=mock(DirectorRunMapper.class);DirectorExperimentRunMapper experimentRuns=mock(DirectorExperimentRunMapper.class);PlayerRunService player=mock(PlayerRunService.class);PlayerRunMapper playerRows=mock(PlayerRunMapper.class);MachineEpisodeBatchMapper batches=mock(MachineEpisodeBatchMapper.class);MachineEpisodeMapper episodes=mock(MachineEpisodeMapper.class);ExperimentComparisonMapper comparisons=mock(ExperimentComparisonMapper.class);GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);when(projects.selectById(1L)).thenReturn(project);when(runs.selectByUuid("run")).thenReturn(DirectorRun.builder().id(9L).runUuid("run").projectId(1L).build());return new Fixture(new PlayerExperimentService(projects,runs,experimentRuns,player,playerRows,batches,episodes,comparisons,json),episodes,comparisons,json);}
    private void add(Fixture f,String uuid,MachineEpisode episode){when(f.episodes.selectByUuid(uuid)).thenReturn(episode);}
    private MachineEpisode episode(String id,String version,String persona,long seed,String outcome,long time){return MachineEpisode.builder().episodeUuid(id).projectId(1L).prototypeVersionUuid(version).sampleSource("MACHINE").personaId(persona).seed(seed).policyId("deterministic").metricVersion("score-delta/1.0").executionStatus("COMPLETED").outcome(outcome).wallDurationMs(time).acceptedActionCount(10).invalidActionCount(0).build();}
    private record Fixture(PlayerExperimentService service,MachineEpisodeMapper episodes,ExperimentComparisonMapper comparisons,ObjectMapper json){}
}
