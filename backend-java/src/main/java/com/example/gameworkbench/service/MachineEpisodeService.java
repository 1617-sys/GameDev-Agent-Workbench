package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.vo.episode.MachineEpisodeAggregateVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeBatchVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeVO;

public interface MachineEpisodeService {
    MachineEpisodeBatchVO persistBatch(Long userId, String projectUuid, String idempotencyKey,
            PersistMachineEpisodeBatchRequest request);
    MachineEpisodeBatchVO getBatch(Long userId, String projectUuid, String batchUuid);
    MachineEpisodeVO getEpisode(Long userId, String projectUuid, String episodeUuid);
    MachineEpisodeAggregateVO aggregate(Long userId, String projectUuid, String prototypeVersionUuid);
}
