package com.example.gameworkbench.vo.episode;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class MachineEpisodeStepPageVO {
    String episodeId;
    int page;
    int size;
    int total;
    List<JsonNode> items;
}
