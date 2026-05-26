package com.example.gameworkbench.dto.gameProject;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GameProjectRequest {

    @NotBlank(message = "Project name is required")
    private String name;

    @NotBlank(message = "Game type is required")
    private String gameType;

    @NotBlank(message = "Target platform is required")
    private String targetPlatform;

    @NotBlank(message = "Project description is required")
    private String description;
}
