package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("director_experiment_run")
public class DirectorExperimentRun {
    @TableId(type=IdType.AUTO)private Long id;
    private String experimentUuid;private Long directorRunId;private Long projectId;
    private String baselineVersionUuid;private String candidateVersionUuid;
    private String baselinePlayerRunUuid;private String candidatePlayerRunUuid;
    private String idempotencyKey;private String requestFingerprint;private String status;
    private LocalDateTime createdAt;private LocalDateTime completedAt;
}
