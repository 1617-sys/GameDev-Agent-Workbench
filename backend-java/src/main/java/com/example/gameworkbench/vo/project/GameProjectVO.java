package com.example.gameworkbench.vo.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameProjectVO {

    private Long id;

    private String projectUuid;

    private Long userId;

    private String name;

    private String gameType;

    private String targetPlatform;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
