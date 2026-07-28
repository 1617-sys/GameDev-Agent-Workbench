package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.*;
import com.example.gameworkbench.export.PrototypePackageBuilder;
import com.example.gameworkbench.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class PrototypeExportServiceImplTest {
    @Test void idempotentReplayReturnsTheSameCompletedFileWithoutRebuilding() throws Exception {
        Fixture f=fixture(); when(f.builder.build(any(),anyString())).thenReturn("zip".getBytes());
        var first=f.service.create(7L,"project","version","same-key"); var replay=f.service.create(7L,"project","version","same-key");
        assertThat(first.getJobUuid()).isEqualTo(replay.getJobUuid()); assertThat(replay.isReused()).isTrue();
        verify(f.builder,times(1)).build(any(),anyString());
    }
    @Test void failedJobRetriesFromTheExactFrozenInputWithoutCallingAnyAgent() throws Exception {
        Fixture f=fixture(); when(f.builder.build(any(),anyString())).thenThrow(new IllegalStateException("disk")).thenReturn("zip".getBytes());
        var failed=f.service.create(7L,"project","version","retry-key"); String frozen=f.stored().getFrozenInputJson();
        assertThat(failed.getStatus()).isEqualTo("FAILED"); var completed=f.service.retry(7L,"project",failed.getJobUuid());
        assertThat(completed.getStatus()).isEqualTo("COMPLETED"); assertThat(f.stored().getFrozenInputJson()).isEqualTo(frozen); assertThat(completed.getAttemptCount()).isEqualTo(2);
    }
    @Test void sameKeyRejectsANewerPlaytestSnapshotAndForeignVersionIsDenied() throws Exception {
        Fixture f=fixture(); when(f.builder.build(any(),anyString())).thenReturn("zip".getBytes()); f.service.create(7L,"project","version","snapshot-key");
        f.aggregate.setSnapshotAt(f.aggregate.getSnapshotAt().plusMinutes(1));
        assertThatThrownBy(()->f.service.create(7L,"project","version","snapshot-key")).isInstanceOf(BusinessException.class).hasMessage(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getMessage());
        f.version.setProjectId(9L); assertThatThrownBy(()->f.service.create(7L,"project","version","other-key")).isInstanceOf(BusinessException.class).hasMessage(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS.getMessage());
    }
    private Fixture fixture() throws Exception {
        GameProjectMapper projects=mock(GameProjectMapper.class); PrototypeVersionMapper versions=mock(PrototypeVersionMapper.class); AgentArtifactMapper artifacts=mock(AgentArtifactMapper.class);
        PrototypePlaytestAggregateMapper aggregates=mock(PrototypePlaytestAggregateMapper.class); PrototypeExportJobMapper jobs=mock(PrototypeExportJobMapper.class); PrototypePackageBuilder builder=mock(PrototypePackageBuilder.class);
        ObjectMapper json=new ObjectMapper().findAndRegisterModules(); GameProject p=new GameProject();p.setId(1L);p.setUserId(7L);p.setProjectUuid("project");p.setName("Project");p.setDescription("Brief");when(projects.selectOne(any())).thenReturn(p);
        String config="{\"metadata\":{}}"; PrototypeVersion v=PrototypeVersion.builder().versionUuid("version").versionNumber(1).projectId(1L).gameConfigArtifactUuid("config").configDigest(digest(config)).runtimeCapabilityVersion("runtime/1").createdAt(LocalDateTime.of(2026,1,1,0,0)).build();when(versions.selectByUuid("version")).thenReturn(v);
        AgentArtifact configArtifact=artifact("config","GAME_CONFIG",config,null);configArtifact.setStepRunId(44L);when(artifacts.selectByArtifactUuid("config")).thenReturn(configArtifact);when(artifacts.selectLatestProjectTypeSource(1L,"RESOURCE_MANIFEST","config")).thenReturn(artifact("manifest","RESOURCE_MANIFEST","{\"resources\":[]}","config"));
        when(artifacts.selectWorkflowInputByOriginStep(44L)).thenReturn("{\"idea\":\"Brief\"}");when(artifacts.selectWorkflowArtifactByOriginStep(44L,"GAME_CONCEPT_RESULT")).thenReturn(artifact("concept","GAME_CONCEPT_RESULT","Concept",null));when(artifacts.selectWorkflowArtifactByOriginStep(44L,"CORE_LOOP_DESIGN_RESULT")).thenReturn(artifact("loop","CORE_LOOP_DESIGN_RESULT","Loop",null));when(artifacts.selectWorkflowArtifactByOriginStep(44L,"TASK_BREAKDOWN_RESULT")).thenReturn(artifact("tasks","TASK_BREAKDOWN_RESULT","Tasks",null));when(artifacts.selectLatestProjectTypeSource(1L,"BALANCE_SUGGESTION","config")).thenReturn(artifact("balance","BALANCE_SUGGESTION","{\"recommendation\":\"Tune\"}","config"));
        PrototypePlaytestAggregate a=PrototypePlaytestAggregate.builder().prototypeVersionUuid("version").endedSessionCount(5).wonCount(2).lostCount(3).abandonedCount(0).totalDurationMs(1000L).totalScore(100L).totalHitCount(2L).totalCollectedCount(4L).totalRestartCount(1L).healthDepletedCount(2).timeExpiredCount(1).snapshotAt(LocalDateTime.of(2026,1,2,0,0)).build();when(aggregates.selectByVersion("version")).thenReturn(a);
        final PrototypeExportJob[] stored={null}; when(jobs.insert(any(PrototypeExportJob.class))).thenAnswer(i->{stored[0]=i.getArgument(0);stored[0].setId(10L);return 1;}); when(jobs.updateById(any(PrototypeExportJob.class))).thenReturn(1); when(jobs.selectIdempotent(eq(7L),eq(1L),anyString())).thenAnswer(i->stored[0]!=null&&stored[0].getIdempotencyKey().equals(i.getArgument(2))?stored[0]:null); when(jobs.selectByUuid(anyString())).thenAnswer(i->stored[0]);
        return new Fixture(new PrototypeExportServiceImpl(projects,versions,artifacts,aggregates,jobs,builder,json),builder,v,a,stored);
    }
    private AgentArtifact artifact(String uuid,String type,String content,String source){return AgentArtifact.builder().artifactUuid(uuid).projectId(1L).artifactType(type).content(content).contentDigest(digest(content)).sourceArtifactUuid(source).build();}
    private String digest(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
    private record Fixture(PrototypeExportServiceImpl service,PrototypePackageBuilder builder,PrototypeVersion version,PrototypePlaytestAggregate aggregate,PrototypeExportJob[] holder){PrototypeExportJob stored(){return holder[0];}}
}
