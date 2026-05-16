package com.example.gameworkbench.dto.gameProject;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GameProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "游戏类型不能为空")
    private String gameType;

    @NotBlank(message = "目标平台不能为空")
    private String targetPlatform;

    @NotBlank(message = "项目描述不能为空")
    private String description;
}
