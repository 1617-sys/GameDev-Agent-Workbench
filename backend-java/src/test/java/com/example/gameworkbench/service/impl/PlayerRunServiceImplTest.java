package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.player.CreatePlayerRunRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlayerRunServiceImplTest {
    @Test void freezesOwnedPrototypeAndOnlyRegisteredPolicyInputs() throws Exception {
        Fixture f=fixture();CreatePlayerRunRequest request=request("baseline-neutral","DETERMINISTIC",null);var result=f.service.submit(7L,"project","player-key-001",null,request);
        assertThat(result.getStatus()).isEqualTo("PENDING");ArgumentCaptor<PlayerRun> created=ArgumentCaptor.forClass(PlayerRun.class);verify(f.runs).insert(created.capture());verify(f.events).publishEvent(any(PlayerRunServiceImpl.PlayerRunRequested.class));
        var frozen=new ObjectMapper().readTree(created.getValue().getRequestJson());assertThat(frozen.path("episodes").get(0).path("correlationId").asText()).isEqualTo(created.getValue().getTraceId());assertThat(frozen.path("episodes").get(0).path("prototype").path("configDigest").asText()).isEqualTo("a".repeat(64));
        CreatePlayerRunRequest invalid=request("ROOT","DETERMINISTIC",null);assertThatThrownBy(()->f.service.submit(7L,"project","player-key-002","trace-player-1234",invalid)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsCrossProjectPrototypeBeforeCreatingRun() throws Exception {Fixture f=fixture();when(f.versions.selectByUuid("version")).thenReturn(PrototypeVersion.builder().versionUuid("version").projectId(99L).build());assertThatThrownBy(()->f.service.submit(7L,"project","player-key-003","trace-player-1234",request("NOVICE","DETERMINISTIC",null))).isInstanceOf(BusinessException.class);verify(f.runs,never()).insert(any(PlayerRun.class));}
    private Fixture fixture() throws Exception {ObjectMapper json=new ObjectMapper();GameProjectMapper projects=mock(GameProjectMapper.class);PrototypeVersionMapper versions=mock(PrototypeVersionMapper.class);AgentArtifactMapper artifacts=mock(AgentArtifactMapper.class);PlayerRunMapper runs=mock(PlayerRunMapper.class);ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);project.setProjectUuid("project");when(projects.selectOne(any())).thenReturn(project);when(versions.selectByUuid("version")).thenReturn(PrototypeVersion.builder().versionUuid("version").projectId(1L).gameConfigArtifactUuid("artifact").configDigest("a".repeat(64)).runtimeCapabilityVersion("arcade/1").build());String config=Files.readString(Path.of("..","docs","requirements","v3","examples","game-config-2.0","valid-minimal.json"));when(artifacts.selectByArtifactUuid("artifact")).thenReturn(AgentArtifact.builder().artifactUuid("artifact").projectId(1L).contentDigest("a".repeat(64)).content(config).build());PlayerRunServiceImpl service=new PlayerRunServiceImpl(projects,versions,artifacts,runs,new GameConfigContract(json),json,events);ReflectionTestUtils.setField(service,"playerModel","test-model");return new Fixture(service,versions,runs,events);}
    private CreatePlayerRunRequest request(String persona,String kind,String model){CreatePlayerRunRequest.Item item=new CreatePlayerRunRequest.Item();item.setClientEpisodeKey("item-1");item.setPersonaId(persona);item.setPolicyKind(kind);item.setSeed(1);item.setPolicySeed(2);item.setMaxSteps(100);item.setModelKey(model);CreatePlayerRunRequest request=new CreatePlayerRunRequest();request.setPrototypeVersionUuid("version");request.setClientBatchKey("batch");request.setEpisodes(List.of(item));request.setConcurrency(1);return request;}
    private record Fixture(PlayerRunServiceImpl service,PrototypeVersionMapper versions,PlayerRunMapper runs,ApplicationEventPublisher events){}
}
