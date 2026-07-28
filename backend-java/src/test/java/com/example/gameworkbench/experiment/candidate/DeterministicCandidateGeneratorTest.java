package com.example.gameworkbench.experiment.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.ExperimentCandidate;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.ExperimentCandidateMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.prototype.PrototypeDraftService;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;

class DeterministicCandidateGeneratorTest {
    @Test void sameInputReplaysStableOrderedPlanWithoutCallingModelOrCreatingAgain(){Fixture f=fixture(100);CandidateGenerationCommand command=new CandidateGenerationCommand("parent","a".repeat(64),Map.of("playerSpeed","INCREASE"),Map.of("playerSpeed",20),1);CandidatePlan first=f.generator.generate(7,1,"run",command),second=f.generator.generate(7,1,"run",command);assertThat(first.planDigest()).isEqualTo(second.planDigest());assertThat(first.candidates()).hasSize(1);assertThat(((Map<?,?>)first.candidates().getFirst().tuning()).get("playerSpeed")).isEqualTo(120);verify(f.drafts,org.mockito.Mockito.times(1)).create(any(Long.class),any(Long.class),any(),any(),any(),any(TunePrototypeVersionRequest.class));}
    @Test void boundaryWithNoEffectiveNeighborReturnsExplicitEmptyResult(){Fixture f=fixture(400);CandidatePlan plan=f.generator.generate(7,1,"run",new CandidateGenerationCommand("parent","a".repeat(64),Map.of("playerSpeed","INCREASE"),Map.of("playerSpeed",20),3));assertThat(plan.status()).isEqualTo("NO_VALID_CANDIDATES");assertThat(plan.candidates()).isEmpty();verify(f.drafts,never()).create(any(Long.class),any(Long.class),any(),any(),any(),any());}
    private Fixture fixture(int speed){GameProjectMapper projects=mock(GameProjectMapper.class);DirectorRunMapper runs=mock(DirectorRunMapper.class);ExperimentCandidateMapper rows=mock(ExperimentCandidateMapper.class);PrototypeVersionService prototypes=mock(PrototypeVersionService.class);PrototypeDraftService drafts=mock(PrototypeDraftService.class);GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);project.setProjectUuid("project");when(projects.selectById(1L)).thenReturn(project);when(runs.selectByUuid("run")).thenReturn(DirectorRun.builder().id(9L).runUuid("run").projectId(1L).build());Map<String,Object> parameters=new LinkedHashMap<>();parameters.put("playerSpeed",speed);when(prototypes.get(7L,"project","parent")).thenReturn(PrototypeVersionVO.builder().versionUuid("parent").parameters(parameters).build());when(drafts.create(any(Long.class),any(Long.class),any(),any(),any(),any())).thenReturn(PrototypeVersionVO.builder().versionUuid("draft-1").configDigest("b".repeat(64)).build());List<ExperimentCandidate> saved=new ArrayList<>();when(rows.selectPlan(any(),any())).thenAnswer(inv->saved.stream().filter(v->v.getInputDigest().equals(inv.getArgument(1))).toList());when(rows.selectMaxOrdinal(any())).thenAnswer(inv->saved.stream().mapToInt(ExperimentCandidate::getOrdinalNumber).max().orElse(0));when(rows.insert(any(ExperimentCandidate.class))).thenAnswer(inv->{saved.add(inv.getArgument(0));return 1;});return new Fixture(new DeterministicCandidateGenerator(projects,runs,rows,prototypes,drafts,new ObjectMapper()),drafts);}
    private record Fixture(DeterministicCandidateGenerator generator,PrototypeDraftService drafts){}
}
