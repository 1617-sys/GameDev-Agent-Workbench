package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.telemetry.*;
import com.example.gameworkbench.entity.*;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.vo.telemetry.TelemetryBatchVO;
import com.fasterxml.jackson.databind.*;

class PlaytestTelemetryServiceImplTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void completeSessionIsServerRecomputedAndBatchReplayDoesNotDoubleCount() throws Exception {
        Fixture f=fixture(); JsonNode config=json.readTree(f.artifact.getContent()); List<TelemetryEventRequest> inputs=new ArrayList<>();
        inputs.add(event(1,"SESSION_STARTED",0,Map.of())); int sequence=2,elapsed=100;
        int expectedScore=0; int target=config.path("objectives").path("targetCollectibles").asInt();
        for(int i=0;i<target;i++){JsonNode item=config.path("entities").path("collectibles").get(i);expectedScore+=item.path("score").asInt();inputs.add(event(sequence++,"ITEM_COLLECTED",elapsed+=100,Map.of("itemId",item.path("id").asText())));}
        inputs.add(event(sequence++,"GAME_WON",elapsed+=100,Map.of())); inputs.add(event(sequence,"SESSION_ENDED",elapsed+=10,Map.of("reason","COMPLETED")));
        TelemetryBatchRequest request=batch("00000000-0000-4000-8000-000000000099",inputs);
        TelemetryBatchVO first=f.service.ingest(7L,"project","session",request,2048); TelemetryBatchVO replay=f.service.ingest(7L,"project","session",request,2048);
        assertThat(first.getSession().getOutcome()).isEqualTo("WON");
        assertThat(first.getSession().getScore()).isEqualTo(expectedScore+config.path("balance").path("winBonus").asInt());
        assertThat(first.getSession().getEventCount()).isEqualTo(inputs.size()); assertThat(replay.isReused()).isTrue();
        assertThat(f.storedEvents).hasSize(inputs.size()); verify(f.aggregates,atLeastOnce()).recompute("version");
    }

    @Test void outOfOrderGapCanBeFilledDuringEndedSessionWindow() throws Exception {
        Fixture f=fixture(); JsonNode config=json.readTree(f.artifact.getContent()); String item=config.path("entities").path("collectibles").get(0).path("id").asText();
        f.service.ingest(7L,"project","session",batch("00000000-0000-4000-8000-000000000010",List.of(event(1,"SESSION_STARTED",0,Map.of()),event(3,"SESSION_ENDED",300,Map.of("reason","USER_EXIT")))),2048);
        TelemetryBatchVO filled=f.service.ingest(7L,"project","session",batch("00000000-0000-4000-8000-000000000011",List.of(event(2,"ITEM_COLLECTED",200,Map.of("itemId",item)))),2048);
        assertThat(filled.getSession().getEventCount()).isEqualTo(3); assertThat(filled.getSession().getCollectedCount()).isEqualTo(1);
        assertThat(f.storedEvents).extracting(PlaytestEvent::getSequenceNumber).containsExactlyInAnyOrder(1,2,3);
    }

    @Test void conflictingSensitiveAndOversizedTelemetryIsAtomicallyRejected() throws Exception {
        Fixture f=fixture(); TelemetryEventRequest sensitive=event(1,"SESSION_STARTED",0,new HashMap<>()); sensitive.getPayload().put("token","secret");
        assertThatThrownBy(()->f.service.ingest(7L,"project","session",batch("00000000-0000-4000-8000-000000000020",List.of(sensitive)),1000))
            .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.TELEMETRY_INVALID.getMessage());
        assertThatThrownBy(()->f.service.ingest(7L,"project","session",batch("00000000-0000-4000-8000-000000000021",List.of(event(1,"SESSION_STARTED",0,Map.of()))),65537))
            .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.TELEMETRY_TOO_LARGE.getMessage());
        assertThat(f.storedEvents).isEmpty();
    }

    @Test void sessionOwnershipIsDerivedAndEnforced() throws Exception {
        Fixture f=fixture(); f.session.setUserId(8L);
        assertThatThrownBy(()->f.service.ingest(7L,"project","session",batch("00000000-0000-4000-8000-000000000030",List.of(event(1,"SESSION_STARTED",0,Map.of()))),1000))
            .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.FORBIDDEN_PLAYTEST_ACCESS.getMessage());
    }

    @Test void perSessionRateAndEventCapsAreEnforced() throws Exception {
        Fixture f=fixture(); when(f.batches.countRecentBySession(10L)).thenReturn(30);
        TelemetryBatchRequest request=batch("00000000-0000-4000-8000-000000000040",List.of(event(1,"SESSION_STARTED",0,Map.of())));
        assertThatThrownBy(()->f.service.ingest(7L,"project","session",request,1000)).isInstanceOf(BusinessException.class)
            .hasMessage(ErrorCode.TELEMETRY_RATE_LIMITED.getMessage());
        when(f.batches.countRecentBySession(10L)).thenReturn(0); f.session.setEventCount(1000);
        assertThatThrownBy(()->f.service.ingest(7L,"project","session",request,1000)).isInstanceOf(BusinessException.class)
            .hasMessage(ErrorCode.TELEMETRY_INVALID.getMessage());
    }

    @Test void concurrentSessionCreationUsesServerUuidsAndVersionOwnership() throws Exception {
        Fixture f=fixture();
        var first=CompletableFuture.supplyAsync(()->f.service.createSession(7L,"project","version"));
        var second=CompletableFuture.supplyAsync(()->f.service.createSession(7L,"project","version"));
        assertThat(first.get().getSessionUuid()).isNotEqualTo(second.get().getSessionUuid());
        assertThat(List.of(first.get().getPrototypeVersionUuid(),second.get().getPrototypeVersionUuid())).containsOnly("version");
    }

    @Test void metricsAreStrictlyIsolatedByPrototypeVersion() throws Exception {
        Fixture f=fixture(); PrototypeVersion other=PrototypeVersion.builder().versionUuid("version-2").projectId(1L).build();
        when(f.versions.selectByUuid("version-2")).thenReturn(other);
        when(f.aggregates.selectByVersion("version")).thenReturn(aggregate("version",5,2));
        when(f.aggregates.selectByVersion("version-2")).thenReturn(aggregate("version-2",8,7));
        assertThat(f.service.metrics(7L,"project","version").getSampleSize()).isEqualTo(5);
        assertThat(f.service.metrics(7L,"project","version-2").getSampleSize()).isEqualTo(8);
        assertThat(f.service.compare(7L,"project","version","version-2").getRight().getWinRate()).isEqualTo(7d/8d);
    }

    private Fixture fixture() throws Exception {
        GameProjectMapper projects=mock(GameProjectMapper.class); PrototypeVersionMapper versions=mock(PrototypeVersionMapper.class); AgentArtifactMapper artifacts=mock(AgentArtifactMapper.class);
        PlaytestSessionMapper sessions=mock(PlaytestSessionMapper.class); PlaytestEventMapper events=mock(PlaytestEventMapper.class); PlaytestEventBatchMapper batches=mock(PlaytestEventBatchMapper.class);
        PrototypePlaytestAggregateMapper aggregates=mock(PrototypePlaytestAggregateMapper.class); BalanceSuggestionRequestMapper suggestions=mock(BalanceSuggestionRequestMapper.class);
        GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);project.setProjectUuid("project");when(projects.selectOne(any())).thenReturn(project);
        PrototypeVersion version=PrototypeVersion.builder().versionUuid("version").projectId(1L).gameConfigArtifactUuid("artifact").configDigest("digest").build();when(versions.selectByUuid("version")).thenReturn(version);
        String content=Files.readString(Path.of("..","docs","requirements","v3","examples","game-config-2.0","valid-minimal.json"));
        AgentArtifact artifact=AgentArtifact.builder().artifactUuid("artifact").projectId(1L).content(content).build();when(artifacts.selectByArtifactUuid("artifact")).thenReturn(artifact);
        PlaytestSession session=PlaytestSession.builder().id(10L).sessionUuid("session").userId(7L).projectId(1L).prototypeVersionUuid("version").status("ACTIVE").startedAt(LocalDateTime.now()).lastSequence(0).eventCount(0).outcome("NONE").score(0).durationMs(0).hitCount(0).collectedCount(0).restartCount(0).build();when(sessions.lockByUuid("session")).thenReturn(session);
        List<PlaytestEvent> stored=Collections.synchronizedList(new ArrayList<>()); Map<String,PlaytestEventBatch> storedBatches=new HashMap<>();
        when(events.selectSessionEvents(10L)).thenAnswer(i->new ArrayList<>(stored)); when(events.selectByUuid(anyString())).thenAnswer(i->stored.stream().filter(e->e.getEventUuid().equals(i.getArgument(0))).findFirst().orElse(null));
        when(events.selectBySequence(eq(10L),anyInt())).thenAnswer(i->stored.stream().filter(e->e.getSequenceNumber().equals(i.getArgument(1))).findFirst().orElse(null));
        when(events.insert(any(PlaytestEvent.class))).thenAnswer(i->{stored.add(i.getArgument(0));return 1;}); when(batches.selectBatch(eq(10L),anyString())).thenAnswer(i->storedBatches.get(i.getArgument(1)));
        when(batches.insert(any(PlaytestEventBatch.class))).thenAnswer(i->{PlaytestEventBatch b=i.getArgument(0);storedBatches.put(b.getBatchUuid(),b);return 1;});
        PlaytestTelemetryServiceImpl service=new PlaytestTelemetryServiceImpl(projects,versions,artifacts,sessions,events,batches,aggregates,suggestions,mock(PythonAgentClient.class),json,new GameConfigContract(json));
        return new Fixture(service,session,artifact,stored,aggregates,batches,versions);
    }
    private TelemetryEventRequest event(int sequence,String type,int elapsed,Map<String,Object> payload){TelemetryEventRequest e=new TelemetryEventRequest();e.setEventUuid(String.format("00000000-0000-4000-8000-%012d",sequence));e.setSequence(sequence);e.setType(type);e.setClientElapsedMs(elapsed);e.setPayload(new HashMap<>(payload));return e;}
    private TelemetryBatchRequest batch(String uuid,List<TelemetryEventRequest> events){TelemetryBatchRequest b=new TelemetryBatchRequest();b.setBatchUuid(uuid);b.setEvents(events);return b;}
    private PrototypePlaytestAggregate aggregate(String uuid,int samples,int wins){return PrototypePlaytestAggregate.builder().prototypeVersionUuid(uuid).endedSessionCount(samples).wonCount(wins).lostCount(samples-wins).abandonedCount(0).totalDurationMs(1000L*samples).totalScore(100L*samples).totalHitCount(0L).totalCollectedCount(0L).totalRestartCount(0L).healthDepletedCount(samples-wins).timeExpiredCount(0).snapshotAt(LocalDateTime.now()).build();}
    private record Fixture(PlaytestTelemetryServiceImpl service,PlaytestSession session,AgentArtifact artifact,List<PlaytestEvent> storedEvents,PrototypePlaytestAggregateMapper aggregates,PlaytestEventBatchMapper batches,PrototypeVersionMapper versions){}
}
