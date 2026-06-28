package com.example.gameworkbench.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameBuildRequest {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("project_uuid")
    private String projectUuid;

    private String title;

    private String content;

    @JsonProperty("game_concept")
    private String gameConcept;

    @JsonProperty("core_loop_design")
    private String coreLoopDesign;

    @JsonProperty("task_breakdown")
    private String taskBreakdown;

    @JsonProperty("game_config")
    private String gameConfig;

    @JsonProperty("game_config_artifact_uuid")
    private String gameConfigArtifactUuid;

    @JsonProperty("artifact_uuids")
    private List<String> artifactUuids;

    @JsonProperty("build_mode")
    private String buildMode;
}
