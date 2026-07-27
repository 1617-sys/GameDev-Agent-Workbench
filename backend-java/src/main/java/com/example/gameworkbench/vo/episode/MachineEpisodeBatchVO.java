package com.example.gameworkbench.vo.episode;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class MachineEpisodeBatchVO {
    String batchId;
    String clientBatchKey;
    String requestFingerprint;
    String status;
    int total;
    int completed;
    int failed;
    int rejected;
    int cancelled;
    boolean reused;
    LocalDateTime createdAt;
    LocalDateTime completedAt;
    List<MachineEpisodeVO> items;
}
