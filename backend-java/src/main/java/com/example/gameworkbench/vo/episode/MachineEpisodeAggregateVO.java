package com.example.gameworkbench.vo.episode;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class MachineEpisodeAggregateVO {
    String sampleSource;
    String prototypeVersionUuid;
    String configDigest;
    int sampleSize;
    int completedCount;
    int failedCount;
    double completionRate;
    long averageDurationMs;
    double averageActionCount;
    Map<String, Integer> terminationReasons;
    List<String> episodeResultRefs;
}
