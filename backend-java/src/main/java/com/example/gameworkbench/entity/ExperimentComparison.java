package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("experiment_comparison")
public class ExperimentComparison {
    @TableId(type=IdType.AUTO) private Long id;
    private String comparisonUuid;
    private Long directorRunId;
    private Long projectId;
    private String baselineVersionUuid;
    private String candidateVersionUuid;
    private String metricVersion;
    private String sampleWindowJson;
    private String episodeRefsJson;
    private String resultJson;
    private String comparisonDigest;
    private Boolean comparable;
    private Boolean recommended;
    private LocalDateTime createdAt;
}
