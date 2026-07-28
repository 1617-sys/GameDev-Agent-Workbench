package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.gameworkbench.client.PlayerApiClient;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.service.MachineEpisodeService;
import com.example.gameworkbench.vo.episode.MachineEpisodeBatchVO;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlayerRunWorkerTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void persistedPythonResponseIsNotReexecutedAndMapsAuditEvidence(){
        PlayerRunMapper runs=mock(PlayerRunMapper.class);PlayerApiClient client=mock(PlayerApiClient.class);MachineEpisodeService episodes=mock(MachineEpisodeService.class);
        String response="""
                {"results":[{"episodeProtocolVersion":"episode/1.0","episodeId":"00000000-0000-4000-8000-000000000001","clientEpisodeKey":"item-1","prototype":{"prototypeVersionUuid":"00000000-0000-4000-8000-000000000010","configDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"simulation":{"protocolVersion":"simulation/1.0","coreVersion":"core/1","seed":1,"maxSteps":10,"observationPolicy":{"kind":"FULL"}},"policy":{"policyId":"deterministic-heuristic","policyVersion":"1.0","policyDigest":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},"persona":{"personaId":"baseline-neutral","personaVersion":"1.0","personaDigest":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"},"model":null,"usage":{"status":"NOT_APPLICABLE"},"audit":{"traceId":"trace-1234"},"timing":{"wallDurationMs":5},"error":{"phase":"RUNNER","code":"PLAYER_INTERNAL_ERROR"},"metricVersion":"score-delta/1.0","executionStatus":"FAILED","terminationReason":null,"outcome":"ERROR","stepCount":0,"acceptedActionCount":0,"invalidActionCount":0,"finalStateHash":null,"finalScore":null,"trajectoryDigest":null,"steps":[]}]}
                """;
        PlayerRun run=PlayerRun.builder().runUuid("run").userId(7L).projectUuid("project").clientBatchKey("batch").status("PERSISTING").responseJson(response).traceId("trace-1234").attempt(1).build();
        when(runs.claim(eq("run"),any())).thenReturn(1);when(runs.selectByUuid("run")).thenReturn(run);when(episodes.persistBatch(eq(7L),eq("project"),eq("player-run/run"),any())).thenReturn(MachineEpisodeBatchVO.builder().batchId("batch-id").status("FAILED").build());
        new PlayerRunWorker(runs,client,episodes,json).execute("run");
        verify(client,never()).runBatch(any(),any());ArgumentCaptor<PersistMachineEpisodeBatchRequest> capture=ArgumentCaptor.forClass(PersistMachineEpisodeBatchRequest.class);verify(episodes).persistBatch(eq(7L),eq("project"),eq("player-run/run"),capture.capture());
        assertThat(capture.getValue().getEpisodes().getFirst().getAudit().path("traceId").asText()).isEqualTo("trace-1234");assertThat(capture.getValue().getEpisodes().getFirst().getError().path("code").asText()).isEqualTo("PLAYER_INTERNAL_ERROR");verify(runs).complete(eq("run"),eq("FAILED"),eq("batch-id"),any());
    }

    @Test void duplicateConsumerClaimDoesNothing(){PlayerRunMapper runs=mock(PlayerRunMapper.class);PlayerApiClient client=mock(PlayerApiClient.class);MachineEpisodeService episodes=mock(MachineEpisodeService.class);when(runs.claim(eq("run"),any())).thenReturn(0);new PlayerRunWorker(runs,client,episodes,json).execute("run");verify(client,never()).runBatch(any(),any());verify(episodes,never()).persistBatch(any(),any(),any(),any());}
}
